package cl.induztek.app

/** Se excluye de la suite offline; quitar notAnnotation para ejecutarla con los servicios. */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class RequiereBackend
