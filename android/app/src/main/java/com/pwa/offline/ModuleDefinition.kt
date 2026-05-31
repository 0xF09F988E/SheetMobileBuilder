package com.pwa.offline

data class ModuleDefinition(
    val id: String,
    val title: String,
    val summary: String,
    val destinationId: Int,
    val iconRes: Int
)

object ModuleRegistry {
    val defaultModules = listOf(
        ModuleDefinition(
            id = "home",
            title = "Inicio",
            summary = "Vista general de tablas, registros y estado actual de la app.",
            destinationId = R.id.homeFragment,
            iconRes = R.drawable.ic_module_home
        ),
        ModuleDefinition(
            id = "dashboard",
            title = "Dashboard",
            summary = "Resumen r\u00e1pido por estado de revisi\u00f3n y conteos agrupados por una columna.",
            destinationId = R.id.dashboardFragment,
            iconRes = R.drawable.ic_module_dashboard
        ),
        ModuleDefinition(
            id = "asset_query",
            title = "Consulta",
            summary = "Buscar registros, revisar su informaci\u00f3n y actualizar sus datos.",
            destinationId = R.id.assetQueryFragment,
            iconRes = R.drawable.ic_module_asset_query
        ),
        ModuleDefinition(
            id = "record_create",
            title = "Nuevo registro",
            summary = "Registrar informaci\u00f3n manualmente seg\u00fan la estructura definida en cada tabla.",
            destinationId = R.id.recordCreateFragment,
            iconRes = R.drawable.ic_module_record_create
        ),
        ModuleDefinition(
            id = "browse",
            title = "Exploraci\u00f3n",
            summary = "Recorrer y consultar los registros almacenados en cada tabla.",
            destinationId = R.id.browseFragment,
            iconRes = R.drawable.ic_module_browse
        ),
        ModuleDefinition(
            id = "schema",
            title = "Dise\u00f1o",
            summary = "Definir tablas, columnas y reglas para organizar la informaci\u00f3n.",
            destinationId = R.id.schemaFragment,
            iconRes = R.drawable.ic_module_schema
        ),
        ModuleDefinition(
            id = "import",
            title = "Importaci\u00f3n",
            summary = "Cargar archivos Excel y convertir su contenido en registros de la app.",
            destinationId = R.id.importFragment,
            iconRes = R.drawable.ic_module_import
        ),
        ModuleDefinition(
            id = "export",
            title = "Exportaci\u00f3n",
            summary = "Generar archivos con la informaci\u00f3n almacenada para descargar o compartir.",
            destinationId = R.id.exportFragment,
            iconRes = R.drawable.ic_module_export
        ),
        ModuleDefinition(
            id = "clone_data",
            title = "Clonar datos",
            summary = "Mover todo el dise\u00f1o y los registros a otro tel\u00e9fono usando un respaldo completo.",
            destinationId = R.id.cloneDataFragment,
            iconRes = R.drawable.ic_module_clone_data
        ),
        ModuleDefinition(
            id = "about",
            title = "Acerca de",
            summary = "Informaci\u00f3n de la app, autor y repositorio.",
            destinationId = R.id.aboutFragment,
            iconRes = R.drawable.ic_module_about
        )
    )
}
