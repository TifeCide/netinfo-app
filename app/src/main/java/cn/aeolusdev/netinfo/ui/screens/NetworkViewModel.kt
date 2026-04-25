package cn.aeolusdev.netinfo.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cn.aeolusdev.netinfo.model.NetworkDetailInfo
import cn.aeolusdev.netinfo.network.NetworkInfoRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class NetworkViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = NetworkInfoRepository(application)

    val networkInfo: StateFlow<NetworkDetailInfo> = repository.networkInfo
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = NetworkDetailInfo()
        )

    init {
        repository.start()
    }

    fun refresh() {
        repository.refresh()
    }

    override fun onCleared() {
        super.onCleared()
        repository.stop()
    }
}
