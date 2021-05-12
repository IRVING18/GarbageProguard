package com.garbage.plugin


import com.garbage.ext.AndroidGarbageCodeExt
import com.garbage.task.GarbageCodeTask
import com.android.build.gradle.AppExtension
import com.android.build.gradle.api.ApplicationVariant
import com.garbage.ext.GarbageCodeConfig
import org.gradle.api.Plugin
import org.gradle.api.Project

class GarbageCodePlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        def android = project.extensions.getByType(AppExtension)
        if (!android) {
            throw IllegalArgumentException("must apply this plugin after 'com.android.application'")
        }
        //获取插件使用者在build中插入的配置
        def generateGarbageCodeExt = project.extensions.create("androidGarbageCode", AndroidGarbageCodeExt)
        //
        project.afterEvaluate {
            //遍历当前所有buildType：debug、release等等
            android.applicationVariants.all { variant ->
                def variantName = variant.name
                //通过用户配置的map，获取当前buildType是否有相关配置。
                Closure<GarbageCodeConfig> garbageCodeConfig = generateGarbageCodeExt.configMap[variantName]
                if (garbageCodeConfig) {
                    //自定义build路径
                    def dir = new File(project.buildDir, "generated/source/garbage/$variantName")
                    def resDir = new File(dir, "res")
                    def javaDir = new File(dir, "java")
                    def manifestFile = new File(dir, "AndroidManifest.xml")
                    //通过buildType查找当前packageName，包名
                    String packageName = findPackageName(variant)
                    //创建并执行task：GarbageCodeTask
                    def generateGarbageCodeTask = project.task("generate${variantName.capitalize()}GarbageCode", type: GarbageCodeTask) {
                        //给GarbageCodeTask变量赋值
                        garbageCodeConfig.delegate = config
                        garbageCodeConfig.resolveStrategy = DELEGATE_FIRST
                        garbageCodeConfig.call()
                        manifestPackageName = packageName
                        outDir = dir
                    }
                    //1、合并AndroidManifest文件
                    //在GarbageCodeTask中会在resDir、javaDir、javaDir路径下去生成相应的文件。这是代码AGarbageCodeTask协定好写死的，并没有通过参数传递，可以自己改
                    //将GarbageCodeTask自动生成的AndroidManifest.xml加入到一个未被占用的manifest位置(如果都占用了就不合并了，通常较少出现全被占用情况)
                    for (int i = variant.sourceSets.size() - 1; i >= 0; i--) {
                        def sourceSet = variant.sourceSets[i]
                        if (!sourceSet.manifestFile.exists()) {
                            android.sourceSets."${sourceSet.name}".manifest.srcFile(manifestFile.absolutePath)
                            break
                        }
                    }
                    //2、合并res资源文件
                    if (variant.respondsTo("registerGeneratedResFolders")) {
                        generateGarbageCodeTask.ext.generatedResFolders = project
                                .files(resDir)
                                .builtBy(generateGarbageCodeTask)
                        variant.registerGeneratedResFolders(generateGarbageCodeTask.generatedResFolders)
                        if (variant.hasProperty("mergeResourcesProvider")) {
                            variant.mergeResourcesProvider.configure { dependsOn(generateGarbageCodeTask) }
                        } else {
                            //noinspection GrDeprecatedAPIUsage
                            variant.mergeResources.dependsOn(generateGarbageCodeTask)
                        }
                    } else {
                        //noinspection GrDeprecatedAPIUsage
                        variant.registerResGeneratingTask(generateGarbageCodeTask, resDir)
                    }
                    //3、合并java文件
                    variant.registerJavaGeneratingTask(generateGarbageCodeTask, javaDir)
                }
            }
        }
    }


    /**
     * 从AndroidManifest.xml找到package name
     * @param variant
     * @return
     */
    static String findPackageName(ApplicationVariant variant) {
        String packageName = null
        for (int i = 0; i < variant.sourceSets.size(); i++) {
            def sourceSet = variant.sourceSets[i]
            if (sourceSet.manifestFile.exists()) {
                def parser = new XmlParser()
                Node node = parser.parse(sourceSet.manifestFile)
                packageName = node.attribute("package")
                if (packageName != null) {
                    break
                }
            }
        }
        return packageName
    }
}