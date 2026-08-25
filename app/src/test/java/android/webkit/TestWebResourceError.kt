package android.webkit

/**
 * Test double for [WebResourceError]: the real constructor is
 * package-private, so the fake must live in the same package.
 */
class TestWebResourceError(
    private val code: Int,
    private val descriptionText: CharSequence
) : WebResourceError() {
    override fun getErrorCode(): Int = code

    override fun getDescription(): CharSequence = descriptionText
}
