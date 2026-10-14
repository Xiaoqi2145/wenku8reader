plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    id("kotlin-parcelize")
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.cyh128.hikari_novel"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.cyh128.hikari_novel"
        minSdk = 24
        targetSdk = 34 //暂时不要升到35，不然之前的edgeToEdge效果会消失
        // 260114 was the last upstream build. This fork has to ship its own
        // number, otherwise the update check cannot tell the two apart and no
        // install would ever be offered the fixes.
        versionCode = 261014
        versionName = "3.9.11"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    splits {
        abi {
            isEnable = true
            isUniversalApk = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }
    }

    signingConfigs {
        create("signing") {
            storeFile = file("$rootDir/app/src/key.jks")
            storePassword = "123456"
            keyAlias = "key"
            keyPassword = "123456"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("signing")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }

        debug {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("signing")
            applicationIdSuffix = ".DEBUG"
            versionNameSuffix = " DEBUG"
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
    }

    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
    }

    lint {
        baseline = file("lint-baseline.xml")
    }
}

dependencies {
    //android相关
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.viewpager2)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)

    //数据库
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)

    //图片查看
    implementation(libs.photoview)

    //rxhttp 网络请求
    implementation(libs.okhttp)
    implementation(libs.rxhttp.core)
    ksp(libs.rxhttp.compiler)

    //lifecycle
    // 原依赖 lifecycle-extensions（2018 年后停更的聚合库），代码中并未使用其独有 API，
    // 改为显式声明实际用到的组件：lifecycleScope/withStarted、viewModelScope、LiveData
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)

    //协程
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    //html解析
    implementation(libs.jsoup)

    //加强版recyclerview
    implementation(libs.byrecyclerview)

    //fragment管理
    implementation(libs.medusa)

    //持久化存储
    //请勿修改此版本，否则app会不支持32位设备（版本号见 gradle/libs.versions.toml 中的 mmkv）
    //noinspection GradleDependency
    implementation(libs.mmkv)

    //图片加载
    implementation(libs.glide.core)
    implementation(libs.glide.okhttp3.integration)

    //refresh layout
    implementation(libs.androidx.swiperefreshlayout)

    //可展开的textview
    implementation(libs.expandabletextview)

    //二级recyclerview
    implementation(libs.expandablerecyclerview)

    //崩溃拦截
    implementation(libs.crashx)

    //statusBar与navigationBar透明
    implementation(libs.immersionbar)

    //hilt依赖注入
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    //json解析
    implementation(libs.gson)

    //事件总线
    implementation(libs.channel)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.androidx.room.testing)

    //内存泄漏查看
    debugImplementation(libs.leakcanary.android)
}
