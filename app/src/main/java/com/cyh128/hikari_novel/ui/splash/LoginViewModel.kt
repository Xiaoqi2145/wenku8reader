package com.cyh128.hikari_novel.ui.splash

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cyh128.hikari_novel.data.model.Event
import com.cyh128.hikari_novel.data.repository.BookshelfSyncer
import com.cyh128.hikari_novel.data.repository.Wenku8Repository
import com.cyh128.hikari_novel.data.di.ApplicationScope
import com.drake.channel.sendEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val wenku8Repository: Wenku8Repository,
    private val bookshelfSyncer: BookshelfSyncer,
    @ApplicationScope private val applicationScope: CoroutineScope
) : ViewModel() {
    private val _isUsernameCorrect = MutableLiveData<Boolean>()
    val isUsernameCorrect: LiveData<Boolean> = _isUsernameCorrect

    private val _isPasswordCorrect = MutableLiveData<Boolean>()
    val isPasswordCorrect: LiveData<Boolean> = _isPasswordCorrect

    private val _isCodeCorrect = MutableLiveData<Boolean>()
    val isCodeCorrect: LiveData<Boolean> = _isCodeCorrect

    fun login(username: String, password: String, checkcode: String, usecookie: String) {
        viewModelScope.launch(Dispatchers.IO) {
            wenku8Repository.login(username, password, checkcode, usecookie)
                .onSuccess { success ->
                    if (success.isCorrect() && success.isLoginSuccessful) { //当密码正确时并且网页提示登录成功时
                        syncBookshelfThenEnterApp()
                    } else if (!success.isCorrect()) { //当密码错误时
                        if (!success.isUsernameCorrect && !success.isPasswordCorrect) {
                            sendEvent(Event.LogInFailureEvent, "event_login_activity")
                        } else {
                            _isUsernameCorrect.postValue(success.isUsernameCorrect)
                            _isPasswordCorrect.postValue(success.isPasswordCorrect)
                            _isCodeCorrect.postValue(success.isCodeCorrect)
                            sendEvent(Event.AuthFailedEvent, "event_login_activity")
                        }
                    } else sendEvent(Event.LogInFailureEvent, "event_login_activity")
                }.onFailure { failure ->
                    sendEvent(Event.NetworkErrorEvent(failure.message), "event_login_activity")
                }
        }
    }

    //保存账户用户名和密码
    fun saveLoginInfo(username: String, password: String) {
        wenku8Repository.username = username
        wenku8Repository.password = password
    }

    /**
     * Establishes the session, then hands the first shelf sync to a scope that
     * survives this screen.
     *
     * `LoginActivity` clears the task as soon as it navigates to `MainActivity`,
     * which cancels `viewModelScope`. Running the sync there meant the six
     * requests could be killed mid-flight on a slow network, and it also held up
     * the transition for as long as the slowest shelf took. The user now reaches
     * the app as soon as the login response is parsed; the shelves appear once
     * the sync commits, which is a single database write.
     */
    private fun syncBookshelfThenEnterApp() {
        sendEvent(Event.LogInSuccessEvent, "event_login_activity")
        applicationScope.launch {
            bookshelfSyncer.sync().onFailure { failure ->
                Log.w("LoginViewModel", "Initial bookshelf sync failed", failure)
            }
        }
    }

    fun getWenku8Node() = wenku8Repository.getWenku8Node()
}
