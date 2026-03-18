package com.example.locationshare

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.locationshare.api.ApiService
import com.example.locationshare.databinding.ActivityProfileBinding
import com.example.locationshare.utils.PrefsManager
import kotlinx.coroutines.launch

/**
 * 个人中心页面
 */
class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding
    private lateinit var prefsManager: PrefsManager
    private lateinit var apiService: ApiService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefsManager = PrefsManager(this)
        apiService = ApiService(this)

        initViews()
        loadUserInfo()
    }

    private fun initViews() {
        // 返回按钮
        binding.btnBack.setOnClickListener {
            finish()
        }

        // 修改昵称
        binding.btnEditName.setOnClickListener {
            showEditNameDialog()
        }

        // 清除数据
        binding.btnClearData.setOnClickListener {
            showClearDataDialog()
        }

        // 退出登录
        binding.btnLogout.setOnClickListener {
            showLogoutDialog()
        }

        // 版本号
        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            binding.tvVersion.text = "版本 ${packageInfo.versionName}"
        } catch (e: Exception) {
            binding.tvVersion.text = "版本 1.0.0"
        }
    }

    private fun loadUserInfo() {
        val user = prefsManager.getUser()
        if (user != null) {
            binding.tvProfileUserName.text = user.userName
            binding.tvProfileUserId.text = "ID: ${user.userId}"
        }
    }

    private fun showEditNameDialog() {
        val currentName = prefsManager.getUserName()

        val editText = EditText(this).apply {
            setText(currentName)
            setSelection(currentName.length)
            hint = "输入新昵称（2-20个字符）"
            setTextColor(resources.getColor(android.R.color.white, null))
            setHintTextColor(resources.getColor(android.R.color.darker_gray, null))
            setPadding(32, 32, 32, 32)
        }

        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("修改昵称")
            .setView(editText)
            .setPositiveButton("保存") { _, _ ->
                val newName = editText.text.toString().trim()
                when {
                    newName.isEmpty() -> showToast("昵称不能为空")
                    newName.length < 2 -> showToast("昵称至少2个字符")
                    newName.length > 20 -> showToast("昵称不能超过20个字符")
                    else -> {
                        lifecycleScope.launch {
                            prefsManager.updateUserName(newName)
                            // 同步到服务器
                            val result = apiService.registerUser(newName)
                            result.onSuccess {
                                showToast("昵称已更新")
                            }.onFailure {
                                showToast("昵称已更新（离线）")
                            }
                            loadUserInfo()
                        }
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showClearDataDialog() {
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("清除所有数据")
            .setMessage("这将删除所有好友、路线和设置，确定要继续吗？")
            .setPositiveButton("清除") { _, _ ->
                prefsManager.clearAll()
                showToast("数据已清除")
                // 回到欢迎界面
                val intent = Intent(this, WelcomeActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showLogoutDialog() {
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("退出登录")
            .setMessage("退出后将清除登录状态，下次需要重新设置昵称。确定要退出吗？")
            .setPositiveButton("退出") { _, _ ->
                prefsManager.clearUserData()
                showToast("已退出登录")
                // 回到欢迎界面
                val intent = Intent(this, WelcomeActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
