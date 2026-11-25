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

package dev.frostlake.config;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Where {@link S3PathResolver} reads S3 objects from when {@code stage.s3.fetch} is on: a file that lives in S3 and is
 * missing below the local root is downloaded through a source before the engine reads it, so a routine's IMPORTS jar
 * or a staged file can come from the real bucket instead of a copy laid out by hand. A source is found with
 * {@link java.util.ServiceLoader}; the engine ships none, and the {@code frostlake-cloud-aws} module provides the AWS SDK one.
 *
 * <p>A source only moves bytes. Which local file a key lands in, the path-safety checks and the caching are the
 * resolver's, so a source never writes outside the target it is handed.</p>
 */
public interface S3ObjectSource {

    /**
     * Takes the engine's settings ({@code stage.s3.region}, {@code stage.s3.endpoint}) before the first fetch. Called
     * once per resolver, so once per engine.
     *
     * @param config the engine's configuration
     */
    void configure(EngineConfig config);

    /**
     * Downloads one object.
     *
     * @param bucket the bucket
     * @param key    the object key, as S3 spells it
     * @param target the file to write; it does not exist yet, and its directory does
     * @return true when the object was written, false when the bucket holds no object under {@code key}
     * @throws IOException when the object exists but cannot be read, or access to it is refused
     */
    boolean fetch(String bucket, String key, Path target) throws IOException;

    /**
     * Lists the keys of every object below a prefix, all pages of them.
     *
     * @param bucket the bucket
     * @param prefix the key prefix, empty for the whole bucket
     * @return the keys, as S3 spells them
     * @throws IOException when the listing is refused or fails
     */
    List<String> list(String bucket, String prefix) throws IOException;
}
