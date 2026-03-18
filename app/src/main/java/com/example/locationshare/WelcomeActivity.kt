package com.example.locationshare

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.locationshare.api.ApiService
import com.example.locationshare.databinding.ActivityWelcomeBinding
import com.example.locationshare.model.User
import com.example.locationshare.utils.PrefsManager
import kotlinx.coroutines.launch

/**
 * 欢迎/注册界面
 * 首次启动时显示，让用户设置昵称
 */
class WelcomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWelcomeBinding
    private lateinit var prefsManager: PrefsManager
    private lateinit var apiService: ApiService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefsManager = PrefsManager(this)
        apiService = ApiService(this)

        // 如果已注册，直接跳转到主界面
        if (prefsManager.isUserRegistered()) {
            navigateToMain()
            return
        }

        binding = ActivityWelcomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initViews()
    }

    private fun initViews() {
        // 显示用户ID（自动生成，只读）
        val userId = prefsManager.getUserId()
        binding.tvUserId.text = "ID: ${userId.takeLast(8)}..."

        // 开始按钮
        binding.btnStart.setOnClickListener {
            val nickName = binding.etNickName.text.toString().trim()

            // 验证昵称
            when {
                nickName.isEmpty() -> {
                    showToast("请输入昵称")
                    return@setOnClickListener
                }
                nickName.length < 2 -> {
                    showToast("昵称至少2个字符")
                    return@setOnClickListener
                }
                nickName.length > 20 -> {
                    showToast("昵称不能超过20个字符")
                    return@setOnClickListener
                }
            }

            // 创建用户并保存
            val user = User(
                userId = userId,
                userName = nickName
            )
            prefsManager.saveUser(user)

            // 同步到服务器
            lifecycleScope.launch {
                binding.btnStart.isEnabled = false
                binding.btnStart.text = "注册中..."

                val result = apiService.registerUser(nickName)
                result.onSuccess {
                    showToast("欢迎，$nickName！")
                    navigateToMain()
                }.onFailure { e ->
                    // 本地保存成功，服务器失败也能继续使用
                    showToast("注册成功（离线模式）")
                    navigateToMain()
                }
            }
        }
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
