package com.bus.app.domain.usecase

import com.bus.app.data.LoginResponse
import com.bus.app.data.repository.BusRepository

class LoginUseCase(private val repository: BusRepository) {
    suspend operator fun invoke(username: String, password: String): LoginResponse? {
        return repository.login(username, password)
    }
}
