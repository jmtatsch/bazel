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

package com.google.devtools.build.lib.skyframe;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.common.collect.Sets;
import com.google.devtools.build.lib.actions.Artifact;
import com.google.devtools.build.lib.actions.BuildFailedException;
import com.google.devtools.build.lib.actions.util.TestAction;
import com.google.devtools.build.lib.testutil.BlazeTestUtils;
import com.google.devtools.build.lib.vfs.FileSystemUtils;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Collection;
import java.util.Collections;

/**
 * Test suite for TimestampBuilder.
 *
 */
@RunWith(JUnit4.class)
public class TimestampBuilderTest extends TimestampBuilderTestCase {

  @Test
  public void testAmnesiacBuilderAlwaysRebuilds() throws Exception {
    // [action] -> hello
    Artifact hello = createDerivedArtifact("hello");
    Button button = createActionButton(emptySet, Sets.newHashSet(hello));

    button.pressed = false;
    buildArtifacts(amnesiacBuilder(), hello);
    assertTrue(button.pressed); // built

    button.pressed = false;
    buildArtifacts(amnesiacBuilder(), hello);
    assertTrue(button.pressed); // rebuilt
  }

  // If we re-use the same builder (even an "amnesiac" builder), it remembers
  // which Actions it has already visited, and doesn't revisit them, even if
  // they would otherwise be rebuilt.
  //
  // That is, Builders conflate traversal and dependency analysis, and don't
  // revisit a node (traversal) even if it needs to be rebuilt (dependency
  // analysis).  We might want to separate these aspects.
  @Test
  public void testBuilderDoesntRevisitActions() throws Exception {
    // [action] -> hello
    Artifact hello = createDerivedArtifact("hello");
    Counter counter = createActionCounter(emptySet, Sets.newHashSet(hello));

    Builder amnesiacBuilder = amnesiacBuilder();

    counter.count = 0;
    buildArtifacts(amnesiacBuilder, hello, hello);
    assertEquals(counter.count, 1); // built only once
  }

  @Test
  public void testBuildingExistingSourcefileSuceeds() throws Exception {
    Artifact hello = createSourceArtifact("hello");
    BlazeTestUtils.makeEmptyFile(hello.getPath());
    buildArtifacts(cachingBuilder(), hello);
  }

  @Test
  public void testBuildingNonexistentSourcefileFails() throws Exception {
    reporter.removeHandler(failFastHandler);
    Artifact hello = createSourceArtifact("hello");
    try {
      buildArtifacts(cachingBuilder(), hello);
      fail("Expected input file to be missing");
    } catch (BuildFailedException e) {
      assertThat(e).hasMessage("missing input file '" + hello.getPath() + "'");
    }
  }

  @Test
  public void testCachingBuilderCachesUntilReset() throws Exception {
    // [action] -> hello
    Artifact hello = createDerivedArtifact("hello");
    Button button = createActionButton(emptySet, Sets.newHashSet(hello));

    button.pressed = false;
    buildArtifacts(cachingBuilder(), hello);
    assertTrue(button.pressed); // built

    button.pressed = false;
    buildArtifacts(cachingBuilder(), hello);
    assertFalse(button.pressed); // not rebuilt

    inMemoryCache.reset();

    button.pressed = false;
    buildArtifacts(cachingBuilder(), hello);
    assertTrue(button.pressed); // rebuilt
  }

  @Test
  public void testUnneededInputs() throws Exception {
    Artifact hello = createSourceArtifact("hello");
    BlazeTestUtils.makeEmptyFile(hello.getPath());
    Artifact optional = createSourceArtifact("hello.optional");
    Artifact goodbye = createDerivedArtifact("goodbye");
    Button button = createActionButton(Sets.newHashSet(hello, optional), Sets.newHashSet(goodbye));

    button.pressed = false;
    buildArtifacts(cachingBuilder(), goodbye);
    assertTrue(button.pressed); // built

    button.pressed = false;
    buildArtifacts(cachingBuilder(), goodbye);
    assertFalse(button.pressed); // not rebuilt

    BlazeTestUtils.makeEmptyFile(optional.getPath());

    button.pressed = false;
    buildArtifacts(cachingBuilder(), goodbye);
    assertTrue(button.pressed); // built

    button.pressed = false;
    buildArtifacts(cachingBuilder(), goodbye);
    assertFalse(button.pressed); // not rebuilt

    optional.getPath().delete();

    button.pressed = false;
    buildArtifacts(cachingBuilder(), goodbye);
    assertTrue(button.pressed); // built

    button.pressed = false;
    buildArtifacts(cachingBuilder(), goodbye);
    assertFalse(button.pressed); // not rebuilt
  }

  @Test
  public void testModifyingInputCausesActionReexecution() throws Exception {
    // hello -> [action] -> goodbye
    Artifact hello = createSourceArtifact("hello");
    BlazeTestUtils.makeEmptyFile(hello.getPath());
    Artifact goodbye = createDerivedArtifact("goodbye");
    Button button = createActionButton(Sets.newHashSet(hello), Sets.newHashSet(goodbye));

    button.pressed = false;
    buildArtifacts(cachingBuilder(), goodbye);
    assertTrue(button.pressed); // built

    button.pressed = false;
    buildArtifacts(cachingBuilder(), goodbye);
    assertFalse(button.pressed); // not rebuilt

    // inMemoryMetadataCache.useFileDigest is false, so new timestamp is enough to force a rebuild.
    FileSystemUtils.touchFile(hello.getPath());

    button.pressed = false;
    buildArtifacts(cachingBuilder(), goodbye);
    assertTrue(button.pressed); // rebuilt

    button.pressed = false;
    buildArtifacts(cachingBuilder(), goodbye);
    assertFalse(button.pressed); // not rebuilt
  }

  @Test
  public void testOnlyModifyingInputContentCausesReexecution() throws Exception {
    // hello -> [action] -> goodbye
    Artifact hello = createSourceArtifact("hello");
    // touch file to create the directory structure
    BlazeTestUtils.makeEmptyFile(hello.getPath());
    FileSystemUtils.writeContentAsLatin1(hello.getPath(), "content1");

    Artifact goodbye = createDerivedArtifact("goodbye");
    Button button = createActionButton(Sets.newHashSet(hello), Sets.newHashSet(goodbye));

    button.pressed = false;
    buildArtifacts(cachingBuilder(), goodbye);
    assertTrue(button.pressed); // built

    button.pressed = false;
    buildArtifacts(cachingBuilder(), goodbye);
    assertFalse(button.pressed); // not rebuilt

    FileSystemUtils.touchFile(hello.getPath());

    button.pressed = false;
    buildArtifacts(cachingBuilder(), goodbye);
    assertFalse(button.pressed); // still not rebuilt

    FileSystemUtils.writeContentAsLatin1(hello.getPath(), "content2");

    button.pressed = false;
    buildArtifacts(cachingBuilder(), goodbye);
    assertTrue(button.pressed); // rebuilt

    button.pressed = false;
    buildArtifacts(cachingBuilder(), goodbye);
    assertFalse(button.pressed); // not rebuilt
  }

  @Test
  public void testModifyingOutputCausesActionReexecution() throws Exception {
    // [action] -> hello
    Artifact hello = createDerivedArtifact("hello");
    Button button = createActionButton(emptySet, Sets.newHashSet(hello));

    button.pressed = false;
    buildArtifacts(cachingBuilder(), hello);
    assertTrue(button.pressed); // built

    button.pressed = false;
    buildArtifacts(cachingBuilder(), hello);
    assertFalse(button.pressed); // not rebuilt

    // Touching the *output* file 'hello' causes 'action' to re-execute, to
    // make things consistent again; this is not what Make would do, but it is
    // correct according to this Builder.
    BlazeTestUtils.changeModtime(hello.getPath());

    button.pressed = false;
    buildArtifacts(cachingBuilder(), hello);
    assertTrue(button.pressed); // rebuilt

    button.pressed = false;
    buildArtifacts(cachingBuilder(), hello);
    assertFalse(button.pressed); // not rebuilt
  }

  @Test
  public void testBuildingTransitivePrerequisites() throws Exception {
    // hello -> [action1] -> wazuup -> [action2] -> goodbye
    Artifact hello = createSourceArtifact("hello");
    BlazeTestUtils.makeEmptyFile(hello.getPath());
    Artifact wazuup = createDerivedArtifact("wazuup");
    Button button1 = createActionButton(Sets.newHashSet(hello), Sets.newHashSet(wazuup));
    Artifact goodbye = createDerivedArtifact("goodbye");
    Button button2 = createActionButton(Sets.newHashSet(wazuup), Sets.newHashSet(goodbye));

    button1.pressed = button2.pressed = false;
    buildArtifacts(cachingBuilder(), wazuup);
    assertTrue(button1.pressed); // built wazuup
    assertFalse(button2.pressed); // goodbye not built

    button1.pressed = button2.pressed = false;
    buildArtifacts(cachingBuilder(), wazuup);
    assertFalse(button1.pressed); // wazuup not rebuilt
    assertFalse(button2.pressed); // goodbye not built

    button1.pressed = button2.pressed = false;
    buildArtifacts(cachingBuilder(), goodbye);
    assertFalse(button1.pressed); // wazuup not rebuilt
    assertTrue(button2.pressed); // built goodbye

    button1.pressed = button2.pressed = false;
    buildArtifacts(cachingBuilder(), goodbye);
    assertFalse(button1.pressed); // wazuup not rebuilt
    assertFalse(button2.pressed); // goodbye not rebuilt

    FileSystemUtils.touchFile(hello.getPath());

    button1.pressed = button2.pressed = false;
    buildArtifacts(cachingBuilder(), goodbye);
    assertTrue(button1.pressed); // hello rebuilt
    assertTrue(button2.pressed); // goodbye rebuilt
  }

  @Test
  public void testWillNotRebuildActionsWithEmptyListOfInputsSpuriously()
      throws Exception {

    Artifact anOutputFile = createDerivedArtifact("anOutputFile");
    Artifact anotherOutputFile = createDerivedArtifact("anotherOutputFile");
    Collection<Artifact> noInputs = Collections.emptySet();

    Button aButton = createActionButton(noInputs, Sets.newHashSet(anOutputFile));
    Button anotherButton = createActionButton(noInputs,
                                              Sets.newHashSet(anotherOutputFile));

    buildArtifacts(cachingBuilder(), anOutputFile, anotherOutputFile);

    assertTrue(aButton.pressed);
    assertTrue(anotherButton.pressed);

    aButton.pressed = anotherButton.pressed = false;

    buildArtifacts(cachingBuilder(), anOutputFile, anotherOutputFile);

    assertFalse(aButton.pressed);
    assertFalse(anotherButton.pressed);
  }

  @Test
  public void testMissingSourceFileIsAnError() throws Exception {
    // A missing input to an action must be treated as an error because there's
    // a risk that the action that consumes it will succeed, but with a
    // different behavior (imagine that it globs over the directory, for
    // example).  It's not ok to simply try the action and let the action
    // report "input file not found".
    //
    // (However, there are exceptions to this principle: C++ compilation
    // actions may depend on non-existent headers from stale .d files.  We need
    // to allow the action to proceed to execution in this case.)

    reporter.removeHandler(failFastHandler);
    Artifact in = createSourceArtifact("in"); // doesn't exist
    Artifact out = createDerivedArtifact("out");

    registerAction(new TestAction(TestAction.NO_EFFECT, Collections.singleton(in),
        Collections.singleton(out)));

    try {
      buildArtifacts(amnesiacBuilder(), out); // fails with ActionExecutionException
      fail();
    } catch (BuildFailedException e) {
      assertThat(e.getMessage()).contains("1 input file(s) do not exist");
    }
  }
}
