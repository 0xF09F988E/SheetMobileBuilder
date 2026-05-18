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
            id = "asset_query",
            title = "Consulta de Activo",
            summary = "Buscar registros, revisar su informacion y actualizar sus datos.",
            destinationId = R.id.assetQueryFragment,
            iconRes = R.drawable.ic_module_asset_query
        ),
        ModuleDefinition(
            id = "record_create",
            title = "Nuevo registro",
            summary = "Registrar informacion manualmente segun la estructura definida en cada tabla.",
            destinationId = R.id.recordCreateFragment,
            iconRes = R.drawable.ic_module_record_create
        ),
        ModuleDefinition(
            id = "browse",
            title = "Exploracion",
            summary = "Recorrer y consultar los registros almacenados en cada tabla.",
            destinationId = R.id.browseFragment,
            iconRes = R.drawable.ic_module_browse
        ),
        ModuleDefinition(
            id = "schema",
            title = "Diseno",
            summary = "Definir tablas, columnas y reglas para organizar la informacion.",
            destinationId = R.id.schemaFragment,
            iconRes = R.drawable.ic_module_schema
        ),
        ModuleDefinition(
            id = "import",
            title = "Importacion",
            summary = "Cargar archivos Excel y convertir su contenido en registros de la app.",
            destinationId = R.id.importFragment,
            iconRes = R.drawable.ic_module_import
        ),
        ModuleDefinition(
            id = "export",
            title = "Exportacion",
            summary = "Generar archivos con la informacion almacenada para descargar o compartir.",
            destinationId = R.id.exportFragment,
            iconRes = R.drawable.ic_module_export
        )
    )
}
