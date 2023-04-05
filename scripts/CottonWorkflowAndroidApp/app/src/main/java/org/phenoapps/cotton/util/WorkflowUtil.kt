package org.phenoapps.cotton.util

class WorkflowUtil {

    companion object {

        const val NumSubSamples = 3 // send, lint and test

        enum class SubSampleType(i: Int) {
            PARENT(0), SEED(1), LINT(2), TEST(3)
        }
    }
}