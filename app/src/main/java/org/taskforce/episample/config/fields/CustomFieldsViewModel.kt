package org.taskforce.episample.config.fields

import android.arch.lifecycle.ViewModel
import android.databinding.ObservableField
import android.support.v4.app.FragmentActivity
import android.view.View
import org.taskforce.episample.R
import org.taskforce.episample.config.base.BaseConfigViewModel
import org.taskforce.episample.config.base.ConfigBuildManager
import org.taskforce.episample.config.language.LanguageService
import org.taskforce.episample.config.sampling.SamplingSelectionFragment
import org.taskforce.episample.core.interfaces.LiveEnumerationSubject

class CustomFieldsViewModel(
        val languageService: LanguageService,
        var createNewField: () -> Unit,
        private val configBuildManager: ConfigBuildManager) :
        ViewModel(), BaseConfigViewModel {
    init {
        languageService.update = {
            hint.set(it.getString(R.string.config_subject_hint))
            pluralHint.set(it.getString(R.string.config_subject_plural_hint))
            fieldHeader.set(it.getString(R.string.config_fields_list_title))
            explanation2.set(it.getString(R.string.config_fields_explanation_2))
            createNewFieldText.set(it.getString(R.string.config_fields_add))
        }
    }

    val subject = object : ObservableField<String>() {
        override fun set(value: String?) {
            super.set(value)
            if (value.isNullOrBlank()) {
                error.set(languageService.getString(R.string.config_fields_add_required))
            } else {
                error.set("")
            }
        }
    }

    val pluralSubject = object : ObservableField<String>() {
        override fun set(value: String?) {
            super.set(value)
            if (value.isNullOrBlank()) {
                pluralError.set(languageService.getString(R.string.config_fields_add_required))
            } else {
                pluralError.set("")
            }
        }
    }

    val primaryLabel = object : ObservableField<String>() {
        override fun set(value: String?) {
            super.set(value)
            if (value.isNullOrBlank()) {
                primaryLabelError.set(languageService.getString(R.string.config_fields_add_required))
            } else {
                primaryLabelError.set("")
            }
        }
    }

    val isValid = object : ObservableField<Boolean>(subject, pluralSubject, primaryLabel) {
        override fun get() = !(subject.get().isNullOrBlank() ||
                pluralSubject.get().isNullOrBlank() ||
                primaryLabel.get().isNullOrBlank())
    }

    val hint = ObservableField(languageService.getString(R.string.config_subject_hint))
    val pluralHint = ObservableField(languageService.getString(R.string.config_subject_plural_hint))
    val primaryLabelHint = ObservableField(languageService.getString(R.string.config_primary_label_hint))

    val error = ObservableField("")

    val pluralError = ObservableField("")

    val primaryLabelError = ObservableField("")

    init {
        // set initial text field values after error observablefields are declared
        subject.set(languageService.getString(R.string.default_subject))
        pluralSubject.set(languageService.getString(R.string.default_subject_plural))
        primaryLabel.set(languageService.getString(R.string.config_primary_label_default_value))
    }

    val fieldHeader = ObservableField(languageService.getString(R.string.config_fields_list_title))

    val explanation2 = object : ObservableField<String>(subject) {
        override fun get(): String? = languageService.getString(R.string.config_fields_explanation_2, subject.get()!!)
    }

    val createNewFieldText = ObservableField(languageService.getString(R.string.config_fields_add))

    val customFieldAdapter = CustomFieldAdapter(
            CustomFieldDisplayFactory(languageService),
            languageService.getString(R.string.config_fields_description_automatic),
            languageService.getString(R.string.config_fields_description_primary),
            languageService.getString(R.string.config_fields_description_contains_pii))

    init {
        (configBuildManager.defaultCustomFields(languageService) - configBuildManager.config.customFields).forEach {
            configBuildManager.addCustomField(it)
        }
        configBuildManager.customFieldObservable.subscribe(customFieldAdapter)
    }

    override val progress: Int
        get() = 4
    override val backEnabled: ObservableField<Boolean> = ObservableField(true)
    override val nextEnabled: ObservableField<Boolean> = isValid

    override fun onNextClicked(view: View) {
        configBuildManager.setEnumerationSubject(LiveEnumerationSubject(
                subject.get()!!,
                pluralSubject.get()!!,
                primaryLabel.get()!!))
        val fragmentManager = (view.context as FragmentActivity).supportFragmentManager
        fragmentManager
                .beginTransaction()
                .replace(R.id.configFrame, SamplingSelectionFragment())
                .addToBackStack(SamplingSelectionFragment::class.qualifiedName)
                .commit()
    }

    override fun onBackClicked(view: View) {
        val fragmentManager = (view.context as FragmentActivity).supportFragmentManager
        fragmentManager.popBackStack()
    }
}

interface CustomFieldDefaultProvider {
    fun defaultCustomFields(languageService: LanguageService): List<CustomField>
}