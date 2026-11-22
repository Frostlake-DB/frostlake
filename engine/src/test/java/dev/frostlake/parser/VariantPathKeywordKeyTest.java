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

package dev.frostlake.parser;

import dev.frostlake.query.MeasuredCellsTestSupport;
import org.junit.jupiter.api.Test;

/**
 * A semi-structured path key after {@code :} or {@code .} may be any word, the reserved ones and DEFAULT included
 * (live-verified).
 */
public class VariantPathKeywordKeyTest extends MeasuredCellsTestSupport {

    @Test
    public void keywordKeys() {
        assertCells(new String[][] {
            {"SELECT v:default FROM (SELECT PARSE_JSON('{\"default\":1}') v)",
                "1"},
            {"SELECT v:a.default FROM (SELECT PARSE_JSON('{\"a\":{\"default\":2}}') v)",
                "2"},
            {"SELECT v:select FROM (SELECT PARSE_JSON('{\"select\":3}') v)",
                "3"},
            {"SELECT v:from FROM (SELECT PARSE_JSON('{\"from\":4}') v)",
                "4"},
            {"SELECT v:order FROM (SELECT PARSE_JSON('{\"order\":5}') v)",
                "5"},
            {"SELECT v:null FROM (SELECT PARSE_JSON('{\"null\":6}') v)",
                "6"},
            {"SELECT v:true FROM (SELECT PARSE_JSON('{\"true\":7}') v)",
                "7"},
            {"SELECT v:false FROM (SELECT PARSE_JSON('{\"false\":8}') v)",
                "8"},
            {"SELECT v:a.select FROM (SELECT PARSE_JSON('{\"a\":{\"select\":9}}') v)",
                "9"},
            {"SELECT v:default.x FROM (SELECT PARSE_JSON('{\"default\":{\"x\":10}}') v)",
                "10"},
            {"SELECT v:default[0] FROM (SELECT PARSE_JSON('{\"default\":[11]}') v)",
                "11"},
            {"SELECT v:DEFAULT FROM (SELECT PARSE_JSON('{\"DEFAULT\":12}') v)",
                "12"},
            {"SELECT v:default::INT + 1 FROM (SELECT PARSE_JSON('{\"default\":1}') v)",
                "2"},
            {"SELECT v:where FROM (SELECT PARSE_JSON('{\"where\":14}') v)",
                "14"},
            {"SELECT v:group FROM (SELECT PARSE_JSON('{\"group\":15}') v)",
                "15"},
            {"SELECT v:and FROM (SELECT PARSE_JSON('{\"and\":16}') v)",
                "16"},
            {"SELECT v:or FROM (SELECT PARSE_JSON('{\"or\":17}') v)",
                "17"},
            {"SELECT v:not FROM (SELECT PARSE_JSON('{\"not\":18}') v)",
                "18"},
            {"SELECT v:is FROM (SELECT PARSE_JSON('{\"is\":19}') v)",
                "19"},
            {"SELECT v:as FROM (SELECT PARSE_JSON('{\"as\":20}') v)",
                "20"},
            {"SELECT v:union FROM (SELECT PARSE_JSON('{\"union\":21}') v)",
                "21"},
            {"SELECT v:a.default.b FROM (SELECT PARSE_JSON('{\"a\":{\"default\":{\"b\":22}}}') v)",
                "22"},
            {"SELECT t.v:default FROM (SELECT PARSE_JSON('{\"default\":23}') v) t",
                "23"},
            {"SELECT v:values FROM (SELECT PARSE_JSON('{\"values\":24}') v)",
                "24"},
            {"SELECT v:in FROM (SELECT PARSE_JSON('{\"in\":25}') v)",
                "25"},
            {"SELECT v:between FROM (SELECT PARSE_JSON('{\"between\":26}') v)",
                "26"},
            {"SELECT v:like FROM (SELECT PARSE_JSON('{\"like\":27}') v)",
                "27"},
            {"SELECT v:join FROM (SELECT PARSE_JSON('{\"join\":28}') v)",
                "28"},
            {"SELECT v:on FROM (SELECT PARSE_JSON('{\"on\":29}') v)",
                "29"},
            {"SELECT v:distinct FROM (SELECT PARSE_JSON('{\"distinct\":30}') v)",
                "30"},
            {"SELECT v:default FROM (SELECT PARSE_JSON('{\"default\":1}') v) WHERE v:default = 1",
                "1"},
            {"SELECT v:a.order FROM (SELECT PARSE_JSON('{\"a\":{\"order\":32}}') v)",
                "32"},
            {"SELECT v:a.null FROM (SELECT PARSE_JSON('{\"a\":{\"null\":33}}') v)",
                "33"},
            {"SELECT v:with FROM (SELECT PARSE_JSON('{\"with\":34}') v)",
                "34"},
            {"SELECT v:case FROM (SELECT PARSE_JSON('{\"case\":35}') v)",
                "35"},
            {"SELECT v:when FROM (SELECT PARSE_JSON('{\"when\":36}') v)",
                "36"},
            {"SELECT v:exists FROM (SELECT PARSE_JSON('{\"exists\":37}') v)",
                "37"},
            {"SELECT v:all FROM (SELECT PARSE_JSON('{\"all\":38}') v)",
                "38"},
            {"SELECT v:a.default FROM (SELECT PARSE_JSON('{\"a\":{\"default\":39}}') v) ORDER BY "
                + "v:a.default",
                "39"},
        });
    }
}
