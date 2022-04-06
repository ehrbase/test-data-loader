/*
 * Copyright 2022 vitasystems GmbH and Hannover Medical School.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.ehrbase.testdata.loader.utils;

import org.springframework.util.FileCopyUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * @author Renaud Subiger
 * @since 1.0
 */
public class FileUtils {

    private FileUtils() {
    }

    public static String getContent(String location) throws IOException {
        var reader = new InputStreamReader(getInputStream(location), StandardCharsets.UTF_8);
        return FileCopyUtils.copyToString(reader);
    }

    public static InputStream getInputStream(String location) {
        return FileUtils.class.getClassLoader().getResourceAsStream(location);
    }
}
