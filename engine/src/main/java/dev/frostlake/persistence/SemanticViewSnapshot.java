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
package dev.frostlake.persistence;

import dev.frostlake.metastore.model.SemanticExpression;
import dev.frostlake.metastore.model.SemanticRelationship;
import dev.frostlake.metastore.model.SemanticTable;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** A semantic view as it survives a restart: its whole definition, with each expression's type. */
public class SemanticViewSnapshot implements Serializable {
    private static final long serialVersionUID = 1L;

    public String name;
    public String comment;
    public String owner;
    public Instant createdAt;
    public Instant lastDdlAt;
    public List<SemanticTable> tables = new ArrayList<>();
    public List<SemanticRelationship> relationships = new ArrayList<>();
    public List<SemanticExpression> expressions = new ArrayList<>();
}
