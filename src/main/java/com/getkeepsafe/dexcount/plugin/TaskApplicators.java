/*
 * Copyright (C) 2015-2021 KeepSafe Software
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.getkeepsafe.dexcount.plugin;

import com.android.repository.Revision;
import com.android.repository.Revision.PreviewComparison;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Utility for obtaining an appropriate task applicator factory for a given
 * Android Gradle Plugin revision.
 */
public final class TaskApplicators {
    private TaskApplicators() {
        // no instances
    }

    public static Optional<TaskApplicator.Factory> getFactory(Revision revision) {
        List<TaskApplicator.Factory> factories = Arrays.asList(
            new SevenOhApplicator.Factory(),
            new FourTwoApplicator.Factory(),
            new FourOneApplicator.Factory(),
            new ThreeSixApplicator.Factory(),
            new ThreeFourApplicator.Factory(),
            new JavaOnlyApplicator.Factory()
        );

        return factories.stream()
            .filter(it -> revision.compareTo(it.getMinimumRevision(), PreviewComparison.IGNORE) >= 0)
            .findFirst();
    }
}
