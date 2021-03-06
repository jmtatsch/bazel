// Copyright 2015 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.google.devtools.build.lib.runtime;

import com.google.devtools.common.options.OptionsParser;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static org.junit.Assert.assertNull;

/**
 * A regression test for {@link BlazeServerStartupOptions}.
 */
@RunWith(JUnit4.class)
public class BlazeServerStartupOptionsTest {

  // A regression test to make sure that the output_base option is correctly parsed if no explicit
  // value is provided.
  @Test
  public void testOutputBaseIsNullByDefault() throws Exception {
    OptionsParser parser = OptionsParser.newOptionsParser(BlazeServerStartupOptions.class);
    parser.parse();
    BlazeServerStartupOptions result = parser.getOptions(BlazeServerStartupOptions.class);
    assertNull(result.outputBase);
  }
}
