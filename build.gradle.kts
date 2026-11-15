plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

dependencies {
    // Shadowed into the jar. relocateShadowedDependencies is off in gradle.properties, which
    // matters for JNA: it locates its own jnidispatch native through the hardcoded
    // com/sun/jna/<platform>/ classpath path, so renaming the package would break it.
    //
    // jopenvr is a JNA binding; it overrides getFieldOrder() in 69 structs, still supported in 5.x.
    "shadowImplementation"("net.java.dev.jna:jna:5.17.0")

    // VRSettings and ProfileManager persist their state as JSON.
    "shadowImplementation"("org.json:json:20250517")

    // Angelica stays optional: everything that touches it goes through AngelicaCompat, which
    // resolves reflectively. Uncomment once we call its API directly.
    // compileOnly("com.github.GTNewHorizons:Angelica:2.2.46:dev")

    // The second hand, also optional. The GTNewHorizons line, not the Modrinth one: it is a
    // Mixin rewrite with a supported way for another mod to take over drawing the off-hand
    // item, which is exactly what a VR mod has to do.
    compileOnly("com.github.GTNewHorizons:Backhand:1.9.0:dev")
}

// Convenience: gradlew build -PmodrinthModsDir=... drops the jar straight into an instance.
val modrinthModsDir: String? = project.findProperty("modrinthModsDir") as String?
if (modrinthModsDir != null) {
    val deployMod by tasks.registering(Copy::class) {
        dependsOn(tasks.named("reobfJar"))
        from(layout.buildDirectory.dir("libs")) {
            include("*.jar")
            exclude("*-dev.jar", "*-sources.jar")
        }
        into(modrinthModsDir)
    }
    tasks.named("build") { finalizedBy(deployMod) }
}
