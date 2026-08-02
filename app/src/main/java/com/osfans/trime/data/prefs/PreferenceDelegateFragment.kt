/*
 * SPDX-FileCopyrightText: 2015 - 2024 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.osfans.trime.data.prefs

import android.os.Bundle
import androidx.annotation.CallSuper
import androidx.annotation.Keep
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreference
import com.osfans.trime.ui.common.PaddingPreferenceFragment
import kotlinx.coroutines.launch

abstract class PreferenceDelegateFragment(
    private val preferenceProvider: PreferenceDelegateProvider,
) : PaddingPreferenceFragment() {
    private val visibility = mutableMapOf<String, Boolean>()

    // it would be better to declare the dependency relationship, rather than reevaluating on each value changed
    @Keep
    private val onValueChangeListener = PreferenceDelegateProvider.OnChangeListener { key ->
        evaluateVisibility()
        syncSwitchState(key)
    }

    init {
        preferenceProvider.registerOnChangeListener(onValueChangeListener)
    }

    open fun onPreferenceUiCreated(screen: PreferenceScreen) {}

    @CallSuper
    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        evaluateVisibility()
        preferenceScreen =
            preferenceManager.createPreferenceScreen(preferenceManager.context).also { screen ->
                preferenceProvider.createUi(screen)
                onPreferenceUiCreated(screen)
            }
    }

    fun evaluateVisibility() {
        val changed = mutableMapOf<String, Boolean>()
        preferenceProvider.preferenceDelegatesUi.forEach { ui ->
            val old = visibility[ui.key]
            val new = ui.isEnabled()
            if (old != null && old != new) {
                changed[ui.key] = new
            }
            visibility[ui.key] = new
        }
        if (changed.isNotEmpty()) {
            lifecycleScope.launch {
                changed.forEach { (key, enable) ->
                    findPreference<Preference>(key)?.isEnabled = enable
                }
            }
        }
    }

    /**
     * A preference can be written by something other than its own control -- two mutually
     * exclusive switches normalising each other, for one. AndroidX Preference reads
     * SharedPreferences when it binds and not again, so a control that did not perform the write
     * would keep showing the old state and swallow the next tap on it.
     */
    private fun syncSwitchState(key: String) {
        val stored = preferenceProvider.preferenceDelegates[key]?.getValue() as? Boolean ?: return
        lifecycleScope.launch {
            if (preferenceScreen == null) return@launch
            val switch = findPreference<Preference>(key) as? SwitchPreference ?: return@launch
            if (switch.isChecked != stored) {
                switch.isChecked = stored
            }
        }
    }

    override fun onDestroy() {
        preferenceProvider.unregisterOnChangeListener(onValueChangeListener)
        super.onDestroy()
    }
}
