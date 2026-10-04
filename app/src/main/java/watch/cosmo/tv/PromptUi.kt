package watch.cosmo.tv

import android.app.Activity
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import watch.cosmo.R

/**
 * Minimal page prompts for a remote. Alerts, confirms, text, auth, and
 * select lists get a large dialog. File inputs are handed to the activity.
 * Unhandled prompt types return null, which GeckoView treats as dismiss.
 */
class PromptUi(
    private val activity: Activity,
    private val filePicker: FilePicker,
) : GeckoSession.PromptDelegate {
    interface FilePicker {
        fun pickFile(
            prompt: GeckoSession.PromptDelegate.FilePrompt,
            result: GeckoResult<GeckoSession.PromptDelegate.PromptResponse>,
        )
    }

    override fun onAlertPrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.AlertPrompt,
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
        return dialogResult { finish ->
            AlertDialog.Builder(activity)
                .setTitle(prompt.title ?: activity.getString(R.string.alert_title))
                .setMessage(prompt.message)
                .setPositiveButton(R.string.ok) { _, _ -> finish { prompt.dismiss() } }
                .setOnDismissListener { finish { prompt.dismiss() } }
                .show()
                .also { enlarge(it) }
        }
    }

    override fun onButtonPrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.ButtonPrompt,
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
        return dialogResult { finish ->
            AlertDialog.Builder(activity)
                .setTitle(prompt.title ?: activity.getString(R.string.alert_title))
                .setMessage(prompt.message)
                .setPositiveButton(R.string.ok) { _, _ ->
                    finish { prompt.confirm(GeckoSession.PromptDelegate.ButtonPrompt.Type.POSITIVE) }
                }
                .setNegativeButton(R.string.cancel) { _, _ ->
                    finish { prompt.confirm(GeckoSession.PromptDelegate.ButtonPrompt.Type.NEGATIVE) }
                }
                .setOnDismissListener { finish { prompt.dismiss() } }
                .show()
                .also { enlarge(it) }
        }
    }

    override fun onTextPrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.TextPrompt,
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
        val view = activity.layoutInflater.inflate(R.layout.dialog_text, null)
        val message = view.findViewById<TextView>(R.id.dialog_message)
        val input = view.findViewById<TextView>(R.id.dialog_input) as android.widget.EditText
        message.text = prompt.message.orEmpty()
        input.setText(prompt.defaultValue.orEmpty())
        return dialogResult { finish ->
            AlertDialog.Builder(activity)
                .setTitle(prompt.title ?: activity.getString(R.string.alert_title))
                .setView(view)
                .setPositiveButton(R.string.ok) { _, _ ->
                    finish { prompt.confirm(input.text.toString()) }
                }
                .setNegativeButton(R.string.cancel) { _, _ -> finish { prompt.dismiss() } }
                .setOnDismissListener { finish { prompt.dismiss() } }
                .show()
        }
    }

    override fun onAuthPrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.AuthPrompt,
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
        val view = activity.layoutInflater.inflate(R.layout.dialog_auth, null)
        val message = view.findViewById<TextView>(R.id.dialog_message)
        val username = view.findViewById<android.widget.EditText>(R.id.auth_username)
        val password = view.findViewById<android.widget.EditText>(R.id.auth_password)
        message.text = prompt.message ?: prompt.authOptions.uri.orEmpty()
        username.setText(prompt.authOptions.username.orEmpty())
        val passwordOnly = prompt.authOptions.flags and
            GeckoSession.PromptDelegate.AuthPrompt.AuthOptions.Flags.ONLY_PASSWORD != 0
        if (passwordOnly) username.visibility = android.view.View.GONE
        return dialogResult { finish ->
            AlertDialog.Builder(activity)
                .setTitle(prompt.title ?: activity.getString(R.string.sign_in))
                .setView(view)
                .setPositiveButton(R.string.ok) { _, _ ->
                    finish {
                        if (passwordOnly) {
                            prompt.confirm(password.text.toString())
                        } else {
                            prompt.confirm(username.text.toString(), password.text.toString())
                        }
                    }
                }
                .setNegativeButton(R.string.cancel) { _, _ -> finish { prompt.dismiss() } }
                .setOnDismissListener { finish { prompt.dismiss() } }
                .show()
        }
    }

    override fun onChoicePrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.ChoicePrompt,
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
        val choices = flatten(prompt.choices)
        if (choices.isEmpty()) {
            return GeckoResult.fromValue(prompt.dismiss())
        }
        if (prompt.type == GeckoSession.PromptDelegate.ChoicePrompt.Type.MULTIPLE) {
            return multipleChoice(prompt, choices)
        }
        return singleChoice(prompt, choices)
    }

    override fun onFilePrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.FilePrompt,
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
        val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
        filePicker.pickFile(prompt, result)
        return result
    }

    override fun onBeforeUnloadPrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.BeforeUnloadPrompt,
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
        return allowDeny(
            activity.getString(R.string.leave_page),
            activity.getString(R.string.leave),
            activity.getString(R.string.stay),
            prompt,
        ) { prompt.confirm(it) }
    }

    override fun onRepostConfirmPrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.RepostConfirmPrompt,
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
        return allowDeny(
            activity.getString(R.string.repost),
            activity.getString(R.string.send),
            activity.getString(R.string.cancel),
            prompt,
        ) { prompt.confirm(it) }
    }

    override fun onPopupPrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.PopupPrompt,
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
        return GeckoResult.fromValue(prompt.confirm(AllowOrDeny.ALLOW))
    }

    override fun onRedirectPrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.RedirectPrompt,
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
        return GeckoResult.fromValue(prompt.confirm(AllowOrDeny.ALLOW))
    }

    private fun allowDeny(
        message: String,
        allowLabel: String,
        denyLabel: String,
        prompt: GeckoSession.PromptDelegate.BasePrompt,
        confirm: (AllowOrDeny) -> GeckoSession.PromptDelegate.PromptResponse,
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
        return dialogResult { finish ->
            AlertDialog.Builder(activity)
                .setMessage(message)
                .setPositiveButton(allowLabel) { _, _ ->
                    finish { confirm(AllowOrDeny.ALLOW) }
                }
                .setNegativeButton(denyLabel) { _, _ ->
                    finish { confirm(AllowOrDeny.DENY) }
                }
                .setOnDismissListener { finish { prompt.dismiss() } }
                .show()
                .also { enlarge(it) }
        }
    }

    private fun singleChoice(
        prompt: GeckoSession.PromptDelegate.ChoicePrompt,
        choices: List<FlatChoice>,
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
        val labels = choices.map { it.label }
        val adapter = object : ArrayAdapter<String>(
            activity,
            android.R.layout.simple_list_item_1,
            labels,
        ) {
            override fun getView(position: Int, convertView: android.view.View?, parent: ViewGroup): android.view.View {
                val view = super.getView(position, convertView, parent) as TextView
                view.textSize = 24f
                val pad = (16 * activity.resources.displayMetrics.density).toInt()
                view.setPadding(pad, pad, pad, pad)
                return view
            }
        }
        return dialogResult { finish ->
            AlertDialog.Builder(activity)
                .setTitle(prompt.title ?: activity.getString(R.string.choose))
                .setAdapter(adapter) { _, which ->
                    finish { prompt.confirm(choices[which].id) }
                }
                .setNegativeButton(R.string.cancel) { _, _ -> finish { prompt.dismiss() } }
                .setOnDismissListener { finish { prompt.dismiss() } }
                .show()
        }
    }

    private fun multipleChoice(
        prompt: GeckoSession.PromptDelegate.ChoicePrompt,
        choices: List<FlatChoice>,
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
        val column = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        }
        val boxes = choices.map { choice ->
            CheckBox(activity).apply {
                text = choice.label
                textSize = 24f
                isChecked = choice.selected
                isFocusable = true
                column.addView(this)
            }
        }
        val scroller = ScrollView(activity).apply { addView(column) }
        return dialogResult { finish ->
            AlertDialog.Builder(activity)
                .setTitle(prompt.title ?: activity.getString(R.string.choose))
                .setView(scroller)
                .setPositiveButton(R.string.ok) { _, _ ->
                    val ids = boxes.mapIndexedNotNull { index, box ->
                        if (box.isChecked) choices[index].id else null
                    }.toTypedArray()
                    finish { prompt.confirm(ids) }
                }
                .setNegativeButton(R.string.cancel) { _, _ -> finish { prompt.dismiss() } }
                .setOnDismissListener { finish { prompt.dismiss() } }
                .show()
        }
    }

    private fun dialogResult(
        show: (finish: (() -> GeckoSession.PromptDelegate.PromptResponse) -> Unit) -> Unit,
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
        val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
        var finished = false
        val finish: (() -> GeckoSession.PromptDelegate.PromptResponse) -> Unit = { block ->
            if (!finished) {
                finished = true
                result.complete(block())
            }
        }
        show(finish)
        return result
    }

    private fun enlarge(dialog: AlertDialog) {
        dialog.findViewById<TextView>(android.R.id.message)?.textSize = 24f
    }

    private data class FlatChoice(val id: String, val label: String, val selected: Boolean)

    private fun flatten(
        choices: Array<GeckoSession.PromptDelegate.ChoicePrompt.Choice>,
        prefix: String = "",
    ): List<FlatChoice> {
        val out = ArrayList<FlatChoice>()
        for (choice in choices) {
            val children = choice.items
            if (children != null && children.isNotEmpty()) {
                val next = if (choice.label.isBlank()) prefix else prefix + choice.label + " / "
                out.addAll(flatten(children, next))
            } else if (!choice.separator && !choice.disabled) {
                out.add(FlatChoice(choice.id, prefix + choice.label, choice.selected))
            }
        }
        return out
    }
}
