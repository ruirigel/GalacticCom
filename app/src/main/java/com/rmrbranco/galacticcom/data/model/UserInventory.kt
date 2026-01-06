package com.rmrbranco.galacticcom.data.model

import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class UserInventory(
    var credits: Long = 0,
    var miningEnergy: Int = 100, 
    var lastEnergyUpdate: Long = 0,
    var lastCollectionTimestamp: Long = 0,
    var miningStartTime: Long = 0, // Novo: Usado para calcular o Stress dos drones
    var planetTotalReserves: Long = 0,
    var resources: MutableMap<String, Int> = mutableMapOf(),
    var items: MutableMap<String, Int> = mutableMapOf()
) {
    // Helper para obter quantidade de um recurso
    fun getResourceAmount(resourceName: String): Int {
        return resources[resourceName] ?: 0
    }

    // Helper para adicionar recurso
    fun addResource(resourceName: String, amount: Int) {
        val current = getResourceAmount(resourceName)
        resources[resourceName] = current + amount
    }
}