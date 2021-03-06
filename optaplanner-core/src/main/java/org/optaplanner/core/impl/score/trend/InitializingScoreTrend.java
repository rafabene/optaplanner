/*
 * Copyright 2014 JBoss Inc
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

package org.optaplanner.core.impl.score.trend;

import org.optaplanner.core.api.score.Score;
import org.optaplanner.core.impl.solution.Solution;

/**
 * Bounds the possible {@link Score}s for a {@link Solution} as more and more variables are initialized
 * (while the already initialized variables don't change).
 */
public enum InitializingScoreTrend {
    /**
     * No predictions can be made.
     */
    ANY,
    /**
     * During initialization, the {@link Score} is monotonically increasing.
     * This means: given a non-fully initialized {@link Solution} with a {@link Score} A,
     * initializing 1 or more variables (without altering the already initialized variables)
     * will give a {@link Solution} for which the {@link Score} is better or equal to A.
     * <p/>
     * In practice, this means that the score constraints of this score level are all positive,
     * and initializing a variable cannot unmatch a already matched positive constraint.
     * <p/>
     * Also implies the perfect minimum score is 0.
     */
    ONLY_UP,
    /**
     * During initialization, the {@link Score} is monotonically decreasing.
     * This means: given a non-fully initialized {@link Solution} with a {@link Score} A,
     * initializing 1 or more variables (without altering the already initialized variables)
     * will give a {@link Solution} for which the {@link Score} is worse or equal to A.
     * <p/>
     * In practice, this means that the score constraints of this score level are all negative,
     * and initializing a variable cannot unmatch a already matched negative constraint.
     * <p/>
     * Also implies the perfect maximum score is 0.
     */
    ONLY_DOWN;
}
