package com.bus.app.domain.usecase

import com.bus.app.data.AuthResult
import com.bus.app.data.LoginRequest
import com.bus.app.data.repository.BusRepository

class LoginUseCase(private val repository: BusRepository) {
    suspend operator fun invoke(username: String, password: String): AuthResult {
        return repository.login(LoginRequest(username = username, password = password))
    }
}
