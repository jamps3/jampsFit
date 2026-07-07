package com.labbaslabs.jampsfit.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.labbaslabs.jampsfit.WatchManager
import com.labbaslabs.jampsfit.WatchState
import com.labbaslabs.jampsfit.database.FoodEntity
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {
    private var watchManager: WatchManager? = null
    
    private val _uiState = MutableStateFlow(WatchState())
    val uiState: StateFlow<WatchState> = _uiState.asStateFlow()

    fun setWatchManager(manager: WatchManager) {
        watchManager = manager
        viewModelScope.launch {
            manager.state.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun startScan() = watchManager?.startScan()
    fun disconnect() = watchManager?.disconnect()
    fun queryCurrentSteps() = watchManager?.queryCurrentSteps()
    fun querySleepBoundaries() = watchManager?.querySleepBoundaries()
    fun findWatch() = watchManager?.findWatch()
    fun syncTime() = watchManager?.syncTime()
    fun startDancingEvent() = watchManager?.startDancingEvent()
    fun stopActiveEvent() = watchManager?.stopActiveEvent()
    fun createFestival() = watchManager?.createFestival()
    fun selectFestival(id: Long) = watchManager?.selectFestival(id)
    fun updateFestivalName(id: Long, name: String) = watchManager?.updateFestivalName(id, name)
    fun updateFestivalImage(id: Long, imageUri: String?) = watchManager?.updateFestivalImage(id, imageUri)
    fun attachEventToSelectedFestival(eventId: Long) = watchManager?.attachEventToSelectedFestival(eventId)
    fun attachEventToFestival(eventId: Long, festivalId: Long) = watchManager?.attachEventToFestival(eventId, festivalId)
    fun deleteEvent(eventId: Long) = watchManager?.deleteEvent(eventId)
    fun addCandy(name: String, size: Int, hours: Int) = watchManager?.addCandy(name, size, hours)
    fun deleteCandy(id: Long) = watchManager?.deleteCandy(id)
    fun addMeal(name: String, type: String, calories: Int, details: String) =
        watchManager?.addMeal(name, type, calories, details)
    fun deleteMeal(id: Long) = watchManager?.deleteMeal(id)
    
    fun updateShutterAction(action: String) = watchManager?.updateShutterAction(action)
    fun updateMusicAction(action: String) = watchManager?.updateMusicAction(action)
    fun updateCustomAction(button: String, action: String) = watchManager?.updateCustomAction(button, action)
    
    fun toggleAutoStart(enabled: Boolean) = watchManager?.toggleAutoStart(enabled)
    fun toggleAutoConnect(enabled: Boolean) = watchManager?.toggleAutoConnect(enabled)
    fun toggleAutoSyncAlarm(enabled: Boolean) = watchManager?.toggleAutoSyncAlarm(enabled)
    fun toggleMuteAlarmSyncNotification(enabled: Boolean) = watchManager?.toggleMuteAlarmSyncNotification(enabled)
    fun toggleAutoSyncTime(enabled: Boolean) = watchManager?.toggleAutoSyncTime(enabled)
    fun updateSyncTimeInterval(hours: Int) = watchManager?.updateSyncTimeInterval(hours)
    
    fun toggleAutoFetchSteps(enabled: Boolean) = watchManager?.toggleAutoFetchSteps(enabled)
    fun toggleAutoFetchBattery(enabled: Boolean) = watchManager?.toggleAutoFetchBattery(enabled)
    fun toggleAutoFetchSleep(enabled: Boolean) = watchManager?.toggleAutoFetchSleep(enabled)
    fun updateStepFetchInterval(minutes: Int) = watchManager?.updateStepFetchInterval(minutes)
    
    fun setAutoHeartRateInterval(minutes: Int) = watchManager?.setAutoHeartRateInterval(minutes)
    fun toggleNotifications(enabled: Boolean) = watchManager?.toggleNotifications(enabled)
    fun toggleIgnoreDuplicates(enabled: Boolean) = watchManager?.toggleIgnoreDuplicates(enabled)
    fun toggleLegacyCallNotifications(enabled: Boolean) = watchManager?.toggleLegacyCallNotifications(enabled)
    fun toggleHrReminder(enabled: Boolean) = watchManager?.toggleHrReminder(enabled)
    fun updateHrReminderInterval(minutes: Int) = watchManager?.updateHrReminderInterval(minutes)
    
    fun addNotificationFilter(pkg: String) = watchManager?.addNotificationFilter(pkg)
    fun removeNotificationFilter(pkg: String) = watchManager?.removeNotificationFilter(pkg)
    
    fun updateBorderColor(color: Int) = watchManager?.updateBorderColor(color)
    fun updateBorderThickness(thickness: Float) = watchManager?.updateBorderThickness(thickness)
    fun updateBorderAlpha(alpha: Float) = watchManager?.updateBorderAlpha(alpha)
    
    fun updateProfile(gender: String, heightCm: Int, weightKg: Float, ageYears: Int) = 
        watchManager?.updateProfile(gender, heightCm, weightKg, ageYears)
    fun saveFood(food: FoodEntity) = watchManager?.saveFood(food)
    fun updateEatSourceFilters(showHome: Boolean, showStore: Boolean, showFastFood: Boolean) =
        watchManager?.updateEatSourceFilters(showHome, showStore, showFastFood)
    fun applyMealCalories(calories: Int) = watchManager?.applyMealCalories(calories)
    fun updateEatCaloriesIncremental(enabled: Boolean) = watchManager?.updateEatCaloriesIncremental(enabled)
    fun resetAppliedMealCalories() = watchManager?.resetAppliedMealCalories()
    fun resetCalorieBaseline() = watchManager?.resetCalorieBaseline()
    fun setShoppingListChecked(id: Long, checked: Boolean) = watchManager?.setShoppingListChecked(id, checked)
    fun deleteFood(id: Long) = watchManager?.deleteFood(id)
    fun setFoodEnabled(id: Long, enabled: Boolean) = watchManager?.setFoodEnabled(id, enabled)
    fun setFoodAvailableAmount(id: Long, amount: Float?) = watchManager?.setFoodAvailableAmount(id, amount)
    fun setFoodOnShoppingList(id: Long, onShoppingList: Boolean) = watchManager?.setFoodOnShoppingList(id, onShoppingList)
    fun markFoodBought(id: Long) = watchManager?.markFoodBought(id)
        
    fun updateVolumeSteps(steps: Int) = watchManager?.updateVolumeSteps(steps)
    fun updateBatteryThreshold(threshold: Int) = watchManager?.updateBatteryThreshold(threshold)
    
    fun setAutoLockSeconds(seconds: Int) = watchManager?.setAutoLockSeconds(seconds)
    fun setQuickViewWindow(startH: Int, startM: Int, endH: Int, em: Int) = 
        watchManager?.setQuickViewWindow(startH, startM, endH, em)
    fun setStepGoal(goal: Int) = watchManager?.setStepGoal(goal)
    fun setWeatherCity(city: String) = watchManager?.setWeatherCity(city)
    fun sendWeatherForecastSample() = watchManager?.sendWeatherForecastSample()
    
    fun startMeasurement(type: String) = watchManager?.startMeasurement(type)
    fun stopMeasurement() = watchManager?.stopMeasurement()
    
    fun clearUnknownPackets() = watchManager?.clearUnknownPackets()
    fun sendLegacyShortNotification(t: String, m: String) = watchManager?.sendLegacyShortNotification(t, m)
    fun sendLegacyCallNotification(t: String, m: String) = watchManager?.sendLegacyCallNotification(t, m)
    fun readBattery() = watchManager?.readBattery()
    fun clearQueue() = watchManager?.clearQueue()
    fun sendRawTest(h: String, u: Boolean) = watchManager?.sendRawTest(h, u)
    fun sendGadgetbridgeProbe(k: String) = watchManager?.sendGadgetbridgeProbe(k)
    fun setAlarm(s: Int, e: Boolean, h: Int, m: Int, r: Int) = watchManager?.setAlarm(s, e, h, m, r)
    fun updateProtocol(h: String, u: String, p: Boolean) = watchManager?.updateProtocol(h, u, p)
    fun setFindingPhone(active: Boolean) = watchManager?.setFindingPhone(active)
}
