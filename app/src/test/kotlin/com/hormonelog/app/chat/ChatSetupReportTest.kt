package com.hormonelog.app.chat

import org.junit.Assert.*
import org.junit.Test

class ChatSetupReportTest {
    @Test fun automaticCodeSubmissionRequiresTheCurrentLoginState() {
        val state = "this-login-state-123456789"
        val url = "https://claude.ai/oauth/authorize?state=$state&scope=test"
        assertEquals("abcdefghijk#$state", ChatSetupReport.matchingCode("abcdefghijk#$state", url))
        assertEquals("abcdefghijk#$state", ChatSetupReport.matchingCode("abcdefghijk#$state", url.replace("claude.ai/oauth", "claude.com/cai/oauth")))
        for (text in listOf("personal-password", "abcdefghijk#another-login-state", "abcdefghijk\n#$state", "https://attacker.test/#$state"))
            assertNull(text, ChatSetupReport.matchingCode(text, url))
        assertNull(ChatSetupReport.matchingCode("abcdefghijk#$state", "https://attacker.test/oauth/authorize?state=$state"))
    }
    @Test fun nativeArchitectureWinsOverAnEmulatedCompatibilityAbi() {
        assertEquals("x86_64", ChatSetupController.termuxRelease(listOf("x86_64", "arm64-v8a"))!!.first)
        assertEquals("arm64-v8a", ChatSetupController.termuxRelease(listOf("arm64-v8a", "armeabi-v7a"))!!.first)
        assertNull(ChatSetupController.termuxRelease(listOf("armeabi-v7a")))
    }
    private val valid = """{"ubuntu":"24.04","claude":"2.1.292","bridgeId":"bundle","loggedIn":true,"authMethod":"claude.ai"}"""
    @Test fun onlyMatchingEnvironmentAndSubscriptionCanProceedToVerification() {
        val report = ChatSetupReport.parse(valid)
        assertTrue(report.installed("bundle"))
        assertTrue(report.subscription)
        assertFalse(report.installed("another-bundle"))
        assertFalse(ChatSetupReport.parse(valid.replace("24.04", "22.04")).installed("bundle"))
        assertFalse(ChatSetupReport.parse(valid.replace("2.1.292", "2.1.291")).installed("bundle"))
        assertFalse(ChatSetupReport.parse(valid.replace("claude.ai", "api_key")).subscription)
        assertFalse(ChatSetupReport.parse(valid.replace("true", "false")).subscription)
    }
    @Test fun missingAndUntrustedStatusCannotEnableChat() {
        val report = ChatSetupReport.parse("{}")
        assertFalse(report.installed("bundle"))
        assertFalse(report.subscription)
    }
    @Test fun loginBrowserOnlyAcceptsOfficialAuthorizationUrls() {
        assertNotNull(ChatSetupReport.officialLoginUrl("https://claude.com/cai/oauth/authorize?state=example"))
        assertNotNull(ChatSetupReport.officialLoginUrl("https://claude.ai/oauth/authorize?state=example"))
        for (url in listOf("http://claude.ai/oauth/authorize", "https://claude.ai.attacker.test/oauth/authorize", "https://claude.ai@attacker.test/oauth/authorize", "https://claude.ai/settings", "intent://claude.ai/oauth/authorize"))
            assertNull(url, ChatSetupReport.officialLoginUrl(url))
    }
}
