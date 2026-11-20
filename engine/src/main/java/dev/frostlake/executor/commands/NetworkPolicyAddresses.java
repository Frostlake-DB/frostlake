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

package dev.frostlake.executor.commands;

import dev.frostlake.executor.SqlCompilationError;

import java.util.ArrayList;
import java.util.List;

/**
 * The addresses a network policy's ALLOWED_IP_LIST and BLOCKED_IP_LIST hold (live-verified). A comma inside an item
 * separates several addresses, and one that ends an item separates nothing; each address is trimmed and then loses
 * one slash at its end, and the policy keeps what is left: {@code (' 1.2.3.4 ', '5.6.7.8/', '1.1.1.1,2.2.2.2', ',')}
 * is the four addresses {@code 1.2.3.4}, {@code 5.6.7.8}, {@code 1.1.1.1} and {@code 2.2.2.2}.
 *
 * <p>An address is {@code a.b.c.d} or {@code a.b.c.d/n}: four whole numbers of 0 to 255 and a prefix of 0 to 32,
 * each read leniently - a sign and leading zeros pass, and blanks pass around the slash but not inside the dotted
 * part, which may end in one dot. The addresses of a list that do not read so are refused together, in the order
 * written and as the policy would have kept them:
 *
 * <pre>
 *   ('abc', '1.2.3.4', 'def/')   Not supported IPv4 format for values '[abc, def]'.  Current support format is …
 *   ('')                         Not supported IPv4 format for values '[]'.  …
 *   ('::1'), ('1.2.3.256'), ('1.2.3.4/33'), ('1.2.3'), ('1. 2.3.4'), ('1.2.3.4/8/9')   refused
 *   ('01.2.3.4/08'), ('+1.2.3.4'), ('1.2.3.4 / 8'), ('1.2.3.4.'), ('0.0.0.0/0')         taken
 * </pre>
 */
final class NetworkPolicyAddresses {

    private NetworkPolicyAddresses() {
    }

    /**
     * Reads a list as the policy keeps it, refusing one that holds an address of another shape.
     *
     * @param written the list's items as the statement wrote them
     * @return the addresses the policy keeps
     */
    static List<String> read(final List<String> written) {
        final List<String> addresses = new ArrayList<>();
        final List<String> failing = new ArrayList<>();
        for (final String item : written) {
            // The separator's trailing empties are dropped: ',' holds no address, '' holds an empty one.
            for (final String part : item.split(",")) {
                String address = part.trim();
                if (address.endsWith("/")) {
                    address = address.substring(0, address.length() - 1);
                }
                addresses.add(address);
                if (!isRange(address)) {
                    failing.add(address);
                }
            }
        }
        if (!failing.isEmpty()) {
            throw new RuntimeException(SqlCompilationError.of("Not supported IPv4 format for values '" + failing
                + "'.  Current support format is single IPv4 address with or without CIDR."));
        }
        return addresses;
    }

    private static boolean isRange(final String address) {
        final String[] halves = address.split("/", -1);
        if (halves.length > 2) {
            return false;
        }
        if (halves.length == 2 && !isNumberUpTo(halves[1].trim(), 32)) {
            return false;
        }
        String dotted = halves[0].trim();
        if (dotted.endsWith(".")) {
            dotted = dotted.substring(0, dotted.length() - 1);
        }
        final String[] numbers = dotted.split("\\.", -1);
        if (numbers.length != 4) {
            return false;
        }
        for (final String number : numbers) {
            if (!isNumberUpTo(number, 255)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isNumberUpTo(final String text, final int most) {
        try {
            final int number = Integer.parseInt(text);
            return number >= 0 && number <= most;
        } catch (final NumberFormatException noNumber) {
            return false;
        }
    }
}
