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
            summary = "Resumen rapido por estado de revision y conteos agrupados por una columna.",
            destinationId = R.id.dashboardFragment,
            iconRes = R.drawable.ic_module_dashboard
        ),
        ModuleDefinition(
            id = "asset_query",
            title = "Consulta",
            summary = "Buscar registros, revisar su información y actualizar sus datos.",
            destinationId = R.id.assetQueryFragment,
            iconRes = R.drawable.ic_module_asset_query
        ),
        ModuleDefinition(
            id = "record_create",
            title = "Nuevo registro",
            summary = "Registrar información manualmente según la estructura definida en cada tabla.",
            destinationId = R.id.recordCreateFragment,
            iconRes = R.drawable.ic_module_record_create
        ),
        ModuleDefinition(
            id = "browse",
            title = "Exploración",
            summary = "Recorrer y consultar los registros almacenados en cada tabla.",
            destinationId = R.id.browseFragment,
            iconRes = R.drawable.ic_module_browse
        ),
        ModuleDefinition(
            id = "schema",
            title = "Diseño",
            summary = "Definir tablas, columnas y reglas para organizar la información.",
            destinationId = R.id.schemaFragment,
            iconRes = R.drawable.ic_module_schema
        ),
        ModuleDefinition(
            id = "import",
            title = "Importación",
            summary = "Cargar archivos Excel y convertir su contenido en registros de la app.",
            destinationId = R.id.importFragment,
            iconRes = R.drawable.ic_module_import
        ),
        ModuleDefinition(
            id = "export",
            title = "Exportación",
            summary = "Generar archivos con la información almacenada para descargar o compartir.",
            destinationId = R.id.exportFragment,
            iconRes = R.drawable.ic_module_export
        ),
        ModuleDefinition(
            id = "about",
            title = "Acerca de",
            summary = "Información de la app, autor y repositorio.",
            destinationId = R.id.aboutFragment,
            iconRes = R.drawable.ic_module_about
        )
    )
}
