package com.meowrescue.game.level

import android.content.Context
import org.json.JSONObject

object LevelLoader {

    fun loadLevel(context: Context, levelId: Int): LevelData {
        val json = context.assets.open("levels/level_$levelId.json")
            .bufferedReader()
            .use { it.readText() }
        val obj = JSONObject(json)

        val starsObj = obj.getJSONObject("stars")
        val stars = StarThresholds(
            one = starsObj.getInt("one"),
            two = starsObj.getInt("two"),
            three = starsObj.getInt("three")
        )

        val ballsArr = obj.getJSONArray("balls")
        val balls = (0 until ballsArr.length()).map { i ->
            val b = ballsArr.getJSONObject(i)
            BallData(
                type = b.getString("type"),
                x = b.getDouble("x").toFloat(),
                y = b.getDouble("y").toFloat()
            )
        }

        val catsArr = obj.getJSONArray("cats")
        val cats = (0 until catsArr.length()).map { i ->
            val c = catsArr.getJSONObject(i)
            CatData(
                x = c.getDouble("x").toFloat(),
                y = c.getDouble("y").toFloat(),
                catId = c.getString("catId")
            )
        }

        val pinsArr = obj.getJSONArray("pins")
        val pins = (0 until pinsArr.length()).map { i ->
            val p = pinsArr.getJSONObject(i)
            PinData(
                type = p.getString("type"),
                x = p.getDouble("x").toFloat(),
                y = p.getDouble("y").toFloat()
            )
        }

        val obstaclesArr = obj.optJSONArray("obstacles") ?: org.json.JSONArray()
        val obstacles = (0 until obstaclesArr.length()).map { i ->
            val o = obstaclesArr.getJSONObject(i)
            ObstacleData(
                type = o.getString("type"),
                x = o.getDouble("x").toFloat(),
                y = o.getDouble("y").toFloat(),
                width = o.getDouble("width").toFloat(),
                height = o.getDouble("height").toFloat()
            )
        }

        val platformsArr = obj.optJSONArray("platforms") ?: org.json.JSONArray()
        val platforms = (0 until platformsArr.length()).map { i ->
            val p = platformsArr.getJSONObject(i)
            PlatformData(
                x = p.getDouble("x").toFloat(),
                y = p.getDouble("y").toFloat(),
                width = p.getDouble("width").toFloat(),
                height = p.getDouble("height").toFloat(),
                angle = p.optDouble("angle", 0.0).toFloat()
            )
        }

        return LevelData(
            levelId = obj.getInt("levelId"),
            name = obj.getString("name"),
            difficulty = obj.getString("difficulty"),
            maxPins = obj.getInt("maxPins"),
            stars = stars,
            balls = balls,
            cats = cats,
            pins = pins,
            obstacles = obstacles,
            platforms = platforms
        )
    }
}
