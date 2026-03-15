package com.cdi.temibridge.handler

import com.google.gson.JsonElement
import com.robotemi.sdk.Robot
import com.robotemi.sdk.navigation.model.Position
import com.cdi.temibridge.server.InvalidParamsException
import com.cdi.temibridge.server.SdkErrorException

class NavigationHandler(private val robot: Robot) {

    fun register(registry: HandlerRegistry) {
        registry.register("navigation.goTo", ::goTo)
        registry.register("navigation.goToPosition", ::goToPosition)
        registry.register("navigation.saveLocation", ::saveLocation)
        registry.register("navigation.deleteLocation", ::deleteLocation)
        registry.register("navigation.getLocations", ::getLocations)
        registry.register("navigation.repose", ::repose)
        registry.register("navigation.getMapData", ::getMapData)
        registry.register("navigation.getMapList", ::getMapList)
        registry.register("navigation.loadMap", ::loadMap)
    }

    private fun goTo(params: JsonElement?, id: Any?): Any? {
        val obj = params?.asJsonObject ?: throw InvalidParamsException("params required")
        val location = obj.get("location")?.asString ?: throw InvalidParamsException("location required")
        robot.goTo(location)
        return mapOf("status" to "accepted", "location" to location)
    }

    private fun goToPosition(params: JsonElement?, id: Any?): Any? {
        val obj = params?.asJsonObject ?: throw InvalidParamsException("params required")
        val x = obj.get("x")?.asFloat ?: throw InvalidParamsException("x required")
        val y = obj.get("y")?.asFloat ?: throw InvalidParamsException("y required")
        val yaw = obj.get("yaw")?.asFloat ?: 0f
        val tiltAngle = obj.get("tiltAngle")?.asInt ?: 0
        robot.goToPosition(Position(x, y, yaw, tiltAngle))
        return mapOf("status" to "accepted")
    }

    private fun saveLocation(params: JsonElement?, id: Any?): Any? {
        val obj = params?.asJsonObject ?: throw InvalidParamsException("params required")
        val name = obj.get("name")?.asString ?: throw InvalidParamsException("name required")
        val result = robot.saveLocation(name)
        return mapOf("success" to result, "name" to name)
    }

    private fun deleteLocation(params: JsonElement?, id: Any?): Any? {
        val obj = params?.asJsonObject ?: throw InvalidParamsException("params required")
        val name = obj.get("name")?.asString ?: throw InvalidParamsException("name required")
        val result = robot.deleteLocation(name)
        return mapOf("success" to result, "name" to name)
    }

    private fun getLocations(params: JsonElement?, id: Any?): Any? {
        return robot.locations
    }

    private fun repose(params: JsonElement?, id: Any?): Any? {
        robot.repose()
        return mapOf("status" to "accepted")
    }

    private fun getMapData(params: JsonElement?, id: Any?): Any? {
        val mapData = robot.getMapData() ?: return null

        val mapInfo = mapData.mapInfo
        val mapImage = mapData.mapImage

        val locations = mapData.locations?.map { layer ->
            mapOf(
                "layerId" to layer.layerId,
                "layerCategory" to layer.layerCategory,
                "layerStatus" to layer.layerStatus,
                "poses" to layer.layerPoses?.map { pose ->
                    mapOf("x" to pose.x, "y" to pose.y, "theta" to pose.theta)
                }
            )
        }

        val virtualWalls = mapData.virtualWalls?.map { layer ->
            mapOf(
                "layerId" to layer.layerId,
                "poses" to layer.layerPoses?.map { pose ->
                    mapOf("x" to pose.x, "y" to pose.y, "theta" to pose.theta)
                }
            )
        }

        val greenPaths = mapData.greenPaths?.map { layer ->
            mapOf(
                "layerId" to layer.layerId,
                "poses" to layer.layerPoses?.map { pose ->
                    mapOf("x" to pose.x, "y" to pose.y, "theta" to pose.theta)
                }
            )
        }

        return mapOf(
            "mapId" to mapData.mapId,
            "mapInfo" to mapOf(
                "width" to mapInfo.width,
                "height" to mapInfo.height,
                "originX" to mapInfo.originX,
                "originY" to mapInfo.originY,
                "resolution" to mapInfo.resolution
            ),
            "mapImage" to if (mapImage != null) mapOf(
                "rows" to mapImage.rows,
                "cols" to mapImage.cols,
                "typeId" to mapImage.typeId
            ) else null,
            "locations" to locations,
            "virtualWalls" to virtualWalls,
            "greenPaths" to greenPaths
        )
    }

    private fun getMapList(params: JsonElement?, id: Any?): Any? {
        return try {
            robot.getMapList().map { mapOf("id" to it.id, "name" to it.name) }
        } catch (e: Exception) {
            throw SdkErrorException("Failed to get map list: ${e.message}")
        }
    }

    private fun loadMap(params: JsonElement?, id: Any?): Any? {
        val obj = params?.asJsonObject ?: throw InvalidParamsException("params required")
        val mapId = obj.get("mapId")?.asString ?: throw InvalidParamsException("mapId required")
        robot.loadMap(mapId)
        return mapOf("status" to "accepted", "mapId" to mapId)
    }
}
