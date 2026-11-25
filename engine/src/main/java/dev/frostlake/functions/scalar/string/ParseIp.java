/*
 * Copyright 2026 MLorek
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.frostlake.functions.scalar.string;

import dev.frostlake.functions.SemiStructuredRejection;
import dev.frostlake.functions.TextArgumentFunction;
import dev.frostlake.functions.scalar.ArrayFunctionHelper;
import dev.frostlake.functions.scalar.SharedFunctionHelpers;
import dev.frostlake.types.ObjectType;

import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;

/**
 * PARSE_IP(ip_address, type [, permissive]) — parses an IPv4 or IPv6 address, optionally followed by a
 * {@code /prefix} network range, into an OBJECT (a real semi-structured value, not its JSON text). {@code type}
 * is {@code 'INET'} or {@code 'CIDR'} in any case, no blank allowed, echoed lowercased as {@code ip_type};
 * anything else is refused as a wrong ip type even when permissive (live-verified).
 *
 * <p>The keys of an IPv4 address are {@code family} (4), {@code host} (the address without the prefix),
 * {@code ip_fields} (the 32-bit address then three zeros), {@code ip_type}, {@code ipv4}, {@code ipv4_range_end}
 * and {@code ipv4_range_start} (only with a prefix), {@code netmask_prefix_length} (NULL without a prefix) and
 * {@code snowflake$type} ({@code "ip_address"}). An IPv6 address — any text holding a colon — has {@code family}
 * 6, {@code hex_ipv6} (32 upper-case hex digits) and, with a prefix, {@code hex_ipv6_range_end} and
 * {@code hex_ipv6_range_start}, then {@code host}, {@code ip_fields} (the four 32-bit words), {@code ip_type},
 * {@code netmask_prefix_length} and {@code snowflake$type}. An empty address has NULL {@code family},
 * {@code host}, {@code ip_fields} and {@code netmask_prefix_length}.
 *
 * <p>The text is read as written, live-verified: no blank is trimmed, a field or prefix must be ASCII digits
 * (hex digits for IPv6, at most four), leading zeros are allowed, an empty prefix after the slash is no prefix,
 * an empty last IPv4 field and an empty first or last IPv6 group stand for 0, and {@code ::} fills the missing
 * IPv6 groups with zeros. With {@code CIDR} and a prefix the address must be the network address. The fields
 * are read in order, so the first bad field names the refusal, before a wrong field count; an IPv6 prefix is
 * checked before too few groups.
 *
 * <p>A malformed address raises "Error parsing IP: ..." unless {@code permissive}, a number rounded to a whole
 * one, is non-zero, when it is an object holding the reason under {@code error}. A NULL argument yields NULL.
 */
public class ParseIp extends TextArgumentFunction {

    private static final String WRONG_TYPE = "Wrong argument provided as ip type when parsing IP: ";
    private static final String PARSE_REFUSAL = "Error parsing IP: ";
    private static final String NON_DIGITAL = "invalid decimal string with non-digital input";
    private static final String INVALID_HEX = "invalid hex string";
    private static final String LONG_HEX_PART = "IPv6 address is invalid: more than 4 hex digits in an IPv6 part";
    private static final BigInteger FULL_MASK = BigInteger.ONE.shiftLeft(128).subtract(BigInteger.ONE);
    private static final long WORD = 0xFFFFFFFFL;

    public ParseIp() { super("PARSE_IP", ObjectType.OBJECT); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null || args.get(1) == null || args.size() > 2 && args.get(2) == null) return null;
        final boolean permissive = args.size() > 2
            && SharedFunctionHelpers.numberArgument(args.get(2)).setScale(0, RoundingMode.HALF_UP).signum() != 0;
        final String type = ipType(args.get(1));
        try {
            return ArrayFunctionHelper.toCanonicalVariant(parse(SharedFunctionHelpers.textOf(args.get(0)), type));
        } catch (final IllegalArgumentException e) {
            if (permissive) {
                final ObjectNode err = ArrayFunctionHelper.MAPPER.createObjectNode();
                err.put("error", e.getMessage());
                return ArrayFunctionHelper.toCanonicalVariant(err);
            }
            throw new RuntimeException(PARSE_REFUSAL + e.getMessage());
        }
    }

    /** The permissive flag is a number: a BOOLEAN there is refused by the argument types (live-verified). */
    @Override
    public SemiStructuredRejection booleanRejection(final int position) {
        return position == 2 ? SemiStructuredRejection.ARGUMENT_TYPES : SemiStructuredRejection.NONE;
    }

    /**
     * The lower-case ip type a type argument names; anything but INET or CIDR is refused.
     *
     * @param argument the non-NULL type argument
     * @return {@code inet} or {@code cidr}
     */
    static String ipType(final Object argument) {
        final String text = SharedFunctionHelpers.textOf(argument);
        final String type = text.toLowerCase(Locale.ROOT);
        if (!type.equals("inet") && !type.equals("cidr")) {
            throw new RuntimeException(WRONG_TYPE + text);
        }
        return type;
    }

    /**
     * The OBJECT an address parses to.
     *
     * @param input the address text, read as written
     * @param type the lower-case ip type
     * @return the object
     * @throws IllegalArgumentException with the reason when the address is malformed
     */
    static ObjectNode parse(final String input, final String type) {
        if (input.isEmpty()) {
            final ObjectNode obj = ArrayFunctionHelper.MAPPER.createObjectNode();
            obj.putNull("family");
            obj.putNull("host");
            obj.putNull("ip_fields");
            obj.put("ip_type", type);
            obj.putNull("netmask_prefix_length");
            obj.put("snowflake$type", "ip_address");
            return obj;
        }
        final boolean ipv6 = input.indexOf(':') >= 0;
        final String family = ipv6 ? "IPv6" : "IPv4";
        final int slash = input.indexOf('/');
        if (slash >= 0 && input.indexOf('/', slash + 1) >= 0) {
            throw invalid(family, "Includes multiple network range prefixes.");
        }
        final String address = slash < 0 ? input : input.substring(0, slash);
        final String prefixText = slash < 0 ? "" : input.substring(slash + 1);
        return ipv6 ? parseIpv6(address, prefixText, type) : parseIpv4(address, prefixText, type);
    }

    private static ObjectNode parseIpv4(final String address, final String prefixText, final String type) {
        final String[] fields = address.split("\\.", -1);
        long ipv4 = 0L;
        for (int i = 0; i < fields.length; i++) {
            final String field = fields[i];
            long value = 0L;
            if (field.isEmpty()) {
                if (i == 0) {
                    throw invalid("IPv4", "Leading dot is invalid.");
                }
                if (i < fields.length - 1) {
                    throw invalid("IPv4", "Includes consecutive delimiters.");
                }
            } else {
                final BigInteger number = decimal(field);
                if (number.compareTo(BigInteger.valueOf(255)) > 0) {
                    throw invalid("IPv4", "One or more fields is higher than 255.");
                }
                value = number.longValue();
            }
            if (i < 4) {
                ipv4 = (ipv4 << 8) | value;
            }
        }
        if (fields.length > 4) {
            throw invalid("IPv4", "IP address includes too many fields.");
        }
        final int prefix = prefix(prefixText, 32, "IPv4");
        if (fields.length < 4) {
            throw invalid("IPv4", "IP address includes too few fields.");
        }
        final long mask = prefix < 0 || prefix == 0 ? 0L : (WORD << (32 - prefix)) & WORD;
        if (prefix >= 0 && type.equals("cidr") && (ipv4 & ~mask & WORD) != 0) {
            throw invalid("IPv4", "Address does not conform to cidr format.");
        }
        final ObjectNode obj = ArrayFunctionHelper.MAPPER.createObjectNode();
        obj.put("family", 4);
        obj.put("host", address);
        final ArrayNode words = obj.putArray("ip_fields");
        words.add(ipv4);
        words.add(0);
        words.add(0);
        words.add(0);
        obj.put("ip_type", type);
        obj.put("ipv4", ipv4);
        if (prefix < 0) {
            obj.putNull("netmask_prefix_length");
        } else {
            final long rangeStart = ipv4 & mask;
            obj.put("ipv4_range_end", rangeStart | (~mask & WORD));
            obj.put("ipv4_range_start", rangeStart);
            obj.put("netmask_prefix_length", prefix);
        }
        obj.put("snowflake$type", "ip_address");
        return obj;
    }

    private static ObjectNode parseIpv6(final String address, final String prefixText, final String type) {
        final String[] parts = address.split(":", -1);
        for (final String part : parts) {
            if (part.length() > 4) {
                throw new IllegalArgumentException(LONG_HEX_PART);
            }
            for (int i = 0; i < part.length(); i++) {
                final char c = part.charAt(i);
                if (!(c >= '0' && c <= '9' || c >= 'a' && c <= 'f' || c >= 'A' && c <= 'F')) {
                    throw new IllegalArgumentException(INVALID_HEX);
                }
            }
        }
        if (parts.length > 8) {
            throw invalid("IPv6", "IP address includes too many fields.");
        }
        int shortenings = 0;
        for (int i = 0; i + 1 < address.length(); i++) {
            if (address.charAt(i) == ':' && address.charAt(i + 1) == ':') {
                shortenings++;
            }
        }
        if (shortenings > 1) {
            throw invalid("IPv6", "Includes ambiguous shortened notation.");
        }
        final int prefix = prefix(prefixText, 128, "IPv6");
        if (shortenings == 0 && parts.length < 8) {
            throw invalid("IPv6", "IP address includes too few fields.");
        }
        final int[] groups = new int[8];
        if (shortenings == 0) {
            for (int i = 0; i < 8; i++) {
                groups[i] = hexGroup(parts[i]);
            }
        } else {
            final int gap = address.indexOf("::");
            final String[] left = groupsOf(address.substring(0, gap));
            final String[] right = groupsOf(address.substring(gap + 2));
            for (int i = 0; i < left.length; i++) {
                groups[i] = hexGroup(left[i]);
            }
            for (int i = 0; i < right.length; i++) {
                groups[8 - right.length + i] = hexGroup(right[i]);
            }
        }
        BigInteger value = BigInteger.ZERO;
        for (int i = 0; i < 8; i++) {
            value = value.shiftLeft(16).or(BigInteger.valueOf(groups[i]));
        }
        final BigInteger mask = prefix <= 0 ? BigInteger.ZERO : FULL_MASK.shiftLeft(128 - prefix).and(FULL_MASK);
        if (prefix >= 0 && type.equals("cidr") && value.and(mask.xor(FULL_MASK)).signum() != 0) {
            throw invalid("IPv6", "Address does not conform to cidr format.");
        }
        final ObjectNode obj = ArrayFunctionHelper.MAPPER.createObjectNode();
        obj.put("family", 6);
        obj.put("hex_ipv6", toHex128(value));
        if (prefix >= 0) {
            final BigInteger rangeStart = value.and(mask);
            obj.put("hex_ipv6_range_end", toHex128(rangeStart.or(mask.xor(FULL_MASK))));
            obj.put("hex_ipv6_range_start", toHex128(rangeStart));
        }
        obj.put("host", address);
        final ArrayNode words = obj.putArray("ip_fields");
        for (int i = 0; i < 4; i++) {
            words.add(((long) groups[2 * i] << 16) | groups[2 * i + 1]);
        }
        obj.put("ip_type", type);
        if (prefix < 0) {
            obj.putNull("netmask_prefix_length");
        } else {
            obj.put("netmask_prefix_length", prefix);
        }
        obj.put("snowflake$type", "ip_address");
        return obj;
    }

    private static String[] groupsOf(final String side) {
        return side.isEmpty() ? new String[0] : side.split(":", -1);
    }

    private static int hexGroup(final String part) {
        return part.isEmpty() ? 0 : Integer.parseInt(part, 16);
    }

    private static BigInteger decimal(final String text) {
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) < '0' || text.charAt(i) > '9') {
                throw new IllegalArgumentException(NON_DIGITAL);
            }
        }
        return new BigInteger(text);
    }

    /** The prefix length a prefix text names, or -1 for none (an empty text). */
    private static int prefix(final String text, final int max, final String family) {
        if (text.isEmpty()) {
            return -1;
        }
        final BigInteger value = decimal(text);
        if (value.compareTo(BigInteger.valueOf(max)) > 0) {
            throw invalid(family, "Network range prefix is too large.");
        }
        return value.intValue();
    }

    private static IllegalArgumentException invalid(final String family, final String reason) {
        return new IllegalArgumentException("Invalid " + family + " address. " + reason);
    }

    private static String toHex128(final BigInteger value) {
        return String.format(Locale.ROOT, "%032X", value);
    }

    @Override
    public int getMinArgCount() { return 2; }
    @Override
    public int getMaxArgCount() { return 3; }
}
