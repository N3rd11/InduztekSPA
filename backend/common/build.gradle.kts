// Seguridad y almacenamiento compartidos; cada servicio tiene su propia base y proceso.
tasks.register<JavaExec>("migrarLaragon") {
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("cl.induztek.common.MigrarLaragon")
    args(rootProject.projectDir.resolve("data").absolutePath)
}
