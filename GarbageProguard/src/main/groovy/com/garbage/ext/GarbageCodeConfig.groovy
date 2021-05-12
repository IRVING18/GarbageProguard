package com.garbage.ext

import org.gradle.api.tasks.Input

class GarbageCodeConfig {
    @Input
    String packageBase = ""
    @Input
    int packageCount = 0
    @Input
    int activityCountPerPackage = 0
    @Input
    boolean excludeActivityJavaFile = false
    @Input
    int otherCountPerPackage = 0
    @Input
    int methodCountPerClass = 0
    @Input
    String resPrefix = "garbage_"
    @Input
    int drawableCount = 0
    @Input
    int stringCount = 0
}