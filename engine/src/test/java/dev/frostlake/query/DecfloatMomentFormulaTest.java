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

package dev.frostlake.query;

import org.junit.jupiter.api.Test;

/**
 * The DECFLOAT variance, standard deviation, skewness and kurtosis follow the formula the account plans over the exact
 * sums of x, x * x, (x * x) * x and ((x * x) * x) * x, every step held to 38 digits: the deviation is the root of the
 * held variance, and the skewness and the kurtosis center the power sums around the held mean (live-verified).
 */
public class DecfloatMomentFormulaTest extends MeasuredCellsTestSupport {

    @Test
    public void momentsFollowThePlannedFormula() {
        assertCells(new String[][] {
            {"SELECT SKEW(x) FROM (SELECT column1::DECFLOAT x FROM VALUES (1),(2),(4))",
                "0.93521952958282449114041779463836907845"},
            {"SELECT SKEW(x) FROM (SELECT column1::DECFLOAT x FROM VALUES (1),(3),(4),(6),(7),(9),(10))",
                "-0.091437453209622087109061711274049517062"},
            {"SELECT KURTOSIS(x) FROM (SELECT column1::DECFLOAT x FROM VALUES "
                + "(1),(3),(4),(6),(7),(9),(10))",
                "-1.1678597516405124699989564854429719395"},
            {"SELECT SKEW(x) FROM (SELECT column1::DECFLOAT x FROM VALUES (1),(2),(4),(8))",
                "1.1376243669582576648487648196737920811"},
            {"SELECT KURTOSIS(x) FROM (SELECT column1::DECFLOAT x FROM VALUES (1),(2),(4),(8))",
                "0.757655954629954196597353497164461248"},
            {"SELECT SKEW(x) FROM (SELECT column1::DECFLOAT x FROM VALUES (43),(84),(40))",
                "1.7030770861459326193874540179814472926"},
            {"SELECT SKEW(x) FROM (SELECT column1::DECFLOAT x FROM VALUES (98),(28),(49))",
                "1.0902905798280032642636765747644622995"},
            {"SELECT SKEW(x) FROM (SELECT column1::DECFLOAT x FROM VALUES "
                + "(16),(-50),(72),(89),(25),(-10),(36))",
                "-0.25629053430806276834047152847059351223"},
            {"SELECT KURTOSIS(x) FROM (SELECT column1::DECFLOAT x FROM VALUES "
                + "(16),(-50),(72),(89),(25),(-10),(36))",
                "-0.2196808956589495005395213222559312455"},
            {"SELECT SKEW(x) FROM (SELECT column1::DECFLOAT x FROM VALUES "
                + "(39),(28),(-22),(49),(-17),(39),(12))",
                "-0.63758037877779348904152357917132892317"},
            {"SELECT KURTOSIS(x) FROM (SELECT column1::DECFLOAT x FROM VALUES "
                + "(39),(28),(-22),(49),(-17),(39),(12))",
                "-1.4296820462158077214108089117832846313"},
            {"SELECT SKEW(x) FROM (SELECT column1::DECFLOAT x FROM VALUES "
                + "(100),(61),(-28),(2),(39),(93),(-37),(25),(82))",
                "-0.26898206449261990421460082112478193766"},
            {"SELECT KURTOSIS(x) FROM (SELECT column1::DECFLOAT x FROM VALUES "
                + "(100),(61),(-28),(2),(39),(93),(-37),(25),(82))",
                "-1.4048936199840875429740906728655268693"},
            {"SELECT SKEW(x) FROM (SELECT column1::DECFLOAT x FROM VALUES "
                + "(40),(99),(97),(67),(1),(36),(46),(0),(64),(13),(5),(-19))",
                "0.30162303289076963483207473804317953417"},
            {"SELECT KURTOSIS(x) FROM (SELECT column1::DECFLOAT x FROM VALUES "
                + "(40),(99),(97),(67),(1),(36),(46),(0),(64),(13),(5),(-19))",
                "-1.0053438250190772963607844761035498869"},
            {"SELECT SKEW(x) FROM (SELECT column1::DECFLOAT x FROM VALUES "
                + "(920.2),(939.39),(469.37726),(616.69324),(630.196),(776.7))",
                "-0.075489095526964391179953812613144142844"},
            {"SELECT KURTOSIS(x) FROM (SELECT column1::DECFLOAT x FROM VALUES "
                + "(920.2),(939.39),(469.37726),(616.69324),(630.196),(776.7))",
                "-1.5248966560247642682544597508328672431"},
            {"SELECT SKEW(x) FROM (SELECT column1::DECFLOAT x FROM VALUES "
                + "(846.8905),(791.89),(973.66),(65.17),(724.767),(583.53241))",
                "-1.614557362009494325190285253028746588"},
            {"SELECT KURTOSIS(x) FROM (SELECT column1::DECFLOAT x FROM VALUES "
                + "(846.8905),(791.89),(973.66),(65.17),(724.767),(583.53241))",
                "2.9708324896924864976600125574694753697"},
            {"SELECT SKEW(x) FROM (SELECT column1::DECFLOAT x FROM VALUES "
                + "(534.466672),(831.6),(385.0),(637.72),(584.83118),(999.18877),(777.728))",
                "0.2223385532991668739958743171688778251"},
            {"SELECT KURTOSIS(x) FROM (SELECT column1::DECFLOAT x FROM VALUES "
                + "(534.466672),(831.6),(385.0),(637.72),(584.83118),(999.18877),(777.728))",
                "-0.3921520289898996763800784087148364251"},
            {"SELECT SKEW(x) FROM (SELECT column1::DECFLOAT x FROM VALUES "
                + "(311.294),(-54.336071),(583.3628),(204.3893),(768.599),(886.47621),(274.68315))",
                "0.10771723912987749441990329178060612748"},
            {"SELECT KURTOSIS(x) FROM (SELECT column1::DECFLOAT x FROM VALUES "
                + "(311.294),(-54.336071),(583.3628),(204.3893),(768.599),(886.47621),(274.68315))",
                "-1.0683512209099088943091101477147383239"},
            {"SELECT SKEW(x) FROM (SELECT column1::DECFLOAT x FROM VALUES "
                + "(908.29066),(573.1),(795.12429),(320.86067),(309.170251),(617.39),(701.49),(88.5568),(755.648),(593.7),(959.12385))",
                "-0.60959431821268556840149760502025548307"},
            {"SELECT KURTOSIS(x) FROM (SELECT column1::DECFLOAT x FROM VALUES "
                + "(908.29066),(573.1),(795.12429),(320.86067),(309.170251),(617.39),(701.49),(88.5568),(755.648),(593.7),(959.12385))",
                "-0.2868673955189732394268857021383375096"},
            {"SELECT SKEW(x) FROM (SELECT column1::DECFLOAT x FROM VALUES "
                + "(631.74),(779.8272),(65.62),(119.69),(619.75676),(-8.16157),(977.3),(79.813992),(719.5694),(996.1),(289.0486),(354.67417))",
                "0.11167445489896893451564423984279223913"},
            {"SELECT KURTOSIS(x) FROM (SELECT column1::DECFLOAT x FROM VALUES "
                + "(631.74),(779.8272),(65.62),(119.69),(619.75676),(-8.16157),(977.3),(79.813992),(719.5694),(996.1),(289.0486),(354.67417))",
                "-1.5459727450647531774736348294558517373"},
            {"SELECT SKEW(x) FROM (SELECT column1::DECFLOAT x FROM VALUES "
                + "(822609035351),(605238653970),(822420837006),(790383937195),(948193870415),(297876715290),(739337461574),(277709900668),(633145146732))",
                "-0.8449186980825124849966679449261367684"},
            {"SELECT KURTOSIS(x) FROM (SELECT column1::DECFLOAT x FROM VALUES "
                + "(822609035351),(605238653970),(822420837006),(790383937195),(948193870415),(297876715290),(739337461574),(277709900668),(633145146732))",
                "-0.4047021302317048541183229633967223301"},
            {"SELECT SKEW(x) FROM (SELECT column1::DECFLOAT x FROM VALUES "
                + "(569120723719),(926461380589),(81406472942),(501592382059),(980379210069),(451814051485),(630631914413),(655645914402),(363008081939))",
                "-0.14085661704669163115096566269292940045"},
            {"SELECT KURTOSIS(x) FROM (SELECT column1::DECFLOAT x FROM VALUES "
                + "(569120723719),(926461380589),(81406472942),(501592382059),(980379210069),(451814051485),(630631914413),(655645914402),(363008081939))",
                "0.2653455584754615517887485005140238603"},
            {"SELECT STDDEV(x) FROM (SELECT column1::DECFLOAT x FROM VALUES (10),(9),(7),(6),(4),(3),(1))",
                "3.2513733362117263061246007189872893903"},
            {"SELECT VAR_POP(x) FROM (SELECT column1::DECFLOAT x FROM VALUES (10),(9),(7),(6),(4),(3),(1))",
                "9.0612244897959183673469387755102040816"},
            {"SELECT STDDEV(x) FROM (SELECT column1::DECFLOAT x FROM VALUES (12),(-33),(-5),(85))",
                "50.374431345012057945627348182418282157"},
            {"SELECT VAR_POP(x) FROM (SELECT column1::DECFLOAT x FROM VALUES (12),(-33),(-5),(85))",
                "1903.1875"},
            {"SELECT STDDEV(x) FROM (SELECT column1::DECFLOAT x FROM VALUES (-9),(-10),(81),(8))",
                "43.131581623368894647573369953629871465"},
            {"SELECT VAR_POP(x) FROM (SELECT column1::DECFLOAT x FROM VALUES (-9),(-10),(81),(8))",
                "1395.25"},
            {"SELECT STDDEV(x) FROM (SELECT column1::DECFLOAT x FROM VALUES "
                + "(822609035351),(605238653970),(822420837006),(790383937195),(948193870415),(297876715290),(739337461574),(277709900668),(633145146732))",
                "234566158566.31416338894099148535660572"},
            {"SELECT VAR_POP(x) FROM (SELECT column1::DECFLOAT x FROM VALUES "
                + "(822609035351),(605238653970),(822420837006),(790383937195),(948193870415),(297876715290),(739337461574),(277709900668),(633145146732))",
                "48907806884050879444416.024691358024691"},
            {"SELECT SKEW(x) FROM (SELECT column1::DECFLOAT x FROM VALUES (5),(5),(5),(5))",
                "REFUSED Division by zero"},
            {"SELECT KURTOSIS(x) FROM (SELECT column1::DECFLOAT x FROM VALUES "
                + "('1e20'),('1e20'),('2e20'),('-3e20'))",
                "3.264579143922483266302786555587474864"},
            {"SELECT SKEW(x) FROM (SELECT column1::DECFLOAT x FROM VALUES "
                + "('0.1'),('0.2'),('0.3'),('0.70000000000000000000000000000000000001'))",
                "1.4430588355323640070678783778745740757"},
            {"SELECT KURTOSIS(x) FROM (SELECT column1::DECFLOAT x FROM VALUES "
                + "('1e-30'),('2e-30'),('4e-30'),('9e-30'))",
                "1.499999999998499999999999999999999999"},
            {"SELECT SKEW(x) FROM (SELECT column1::DECFLOAT x FROM VALUES ('1e38'),('1'),('-1e38'),('2'))",
                "-5.5113519212649063969045202434487275766E-38"},
            {"SELECT VAR_SAMP(x) FROM (SELECT column1::DECFLOAT x FROM VALUES "
                + "('1e38'),('1'),('-1e38'),('2'))",
                "6.6666666666666666666666666666666666667E+75"},
            {"SELECT SKEW(x) OVER () FROM (SELECT column1::DECFLOAT x FROM VALUES "
                + "(1),(3),(4),(6),(7),(9),(10)) QUALIFY ROW_NUMBER() OVER (ORDER BY x) = 1",
                "-0.091437453209622087109061711274049517062"},
        });
    }
}
