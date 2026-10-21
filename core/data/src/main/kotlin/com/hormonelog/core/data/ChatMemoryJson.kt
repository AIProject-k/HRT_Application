package com.hormonelog.core.data

import com.hormonelog.core.domain.*
import org.json.JSONArray
import org.json.JSONObject

internal object ChatMemoryJson {
    fun toJson(memory: ChatMemory): JSONObject = JSONObject().put("name", memory.name).apply {
        when (memory) {
            is ChatMemory.Alias -> {
                put("type", "alias"); put("drug", memory.drug.name); put("product", memory.product.name)
            }
            is ChatMemory.Shortcut -> {
                put("type", "shortcut")
                put("doses", JSONArray().apply { memory.doses.forEach { d -> put(JSONObject()
                    .put("drug", d.drug.name).put("route", d.route.name).put("amountMg", d.amountMg).put("product", d.product.name)) } })
            }
        }
    }

    fun fromJson(o: JSONObject): ChatMemory = when (o.getString("type")) {
        "alias" -> ChatMemory.Alias(o.getString("name"), Drug.valueOf(o.getString("drug")), product(o))
        "shortcut" -> {
            val doses = o.getJSONArray("doses")
            require(doses.length() in 1..5)
            ChatMemory.Shortcut(o.getString("name"), (0 until doses.length()).map { i -> doses.getJSONObject(i).let { d ->
                ChatMemory.Dose(Drug.valueOf(d.getString("drug")), Route.valueOf(d.getString("route")), d.getDouble("amountMg"), product(d))
            } })
        }
        else -> throw IllegalArgumentException("Unknown memory type")
    }

    private fun product(o: JSONObject) = P4Product.valueOf(o.optString("product", P4Product.UNKNOWN.name))
}
