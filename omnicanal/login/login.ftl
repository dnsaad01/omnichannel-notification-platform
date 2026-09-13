<#import "template.ftl" as layout>
<@layout.registrationLayout displayInfo=social.displayInfo displayMessage=!messagesPerField.existsError('username','password') showAnotherWayIfPresent=true; section>

    <#if section = "header">
        ${msg("doLogIn")}

    <#elseif section = "form">
        <#if realm.password>
            <form id="kc-form-login" class="oc-form" onsubmit="login.disabled = true; return true;" action="${url.loginAction}" method="post">

                <div class="oc-field">
                    <label for="username" class="oc-label">
                        <#if !realm.loginWithEmailAllowed>${msg("username")}
                        <#elseif !realm.registrationEmailAsUsername>${msg("usernameOrEmail")}
                        <#else>${msg("email")}
                        </#if>
                    </label>

                    <#if usernameEditDisabled??>
                        <input tabindex="1" id="username" class="oc-input" name="username"
                               value="${(login.username!'')}" type="text" disabled />
                    <#else>
                        <input tabindex="1" id="username" class="oc-input" name="username"
                               value="${(login.username!'')}" type="text" autofocus autocomplete="username"
                               aria-invalid="<#if messagesPerField.existsError('username','password')>true</#if>" />
                    </#if>
                </div>

                <div class="oc-field">
                    <div class="oc-label-row">
                        <label for="password" class="oc-label">${msg("password")}</label>
                        <#if realm.resetPasswordAllowed>
                            <a tabindex="5" href="${url.loginResetCredentialsUrl}" class="oc-link oc-link-small">
                                ${msg("doForgotPassword")}
                            </a>
                        </#if>
                    </div>
                    <div class="oc-password-wrapper">
                        <input tabindex="2" id="password" class="oc-input" name="password" type="password"
                               autocomplete="current-password"
                               aria-invalid="<#if messagesPerField.existsError('username','password')>true</#if>" />
                        <button type="button" class="oc-password-toggle" id="oc-password-toggle"
                                aria-label="${msg("showPassword")!'Afficher le mot de passe'}"
                                onclick="
                                    var pw=document.getElementById('password');
                                    var showing = pw.type === 'text';
                                    pw.type = showing ? 'password' : 'text';
                                    this.textContent = showing ? '${msg("showPassword")!'Afficher'}' : '${msg("hidePassword")!'Masquer'}';
                                ">${msg("showPassword")!'Afficher'}</button>
                    </div>
                </div>

                <#if messagesPerField.existsError('username','password')>
                    <div class="oc-field-error" aria-live="polite">
                        ${kcSanitize(messagesPerField.getFirstError('username','password'))?no_esc}
                    </div>
                </#if>

                <#if realm.rememberMe && !usernameEditDisabled??>
                    <div class="oc-checkbox-row">
                        <input tabindex="3" id="rememberMe" name="rememberMe" type="checkbox" <#if login.rememberMe??>checked</#if> />
                        <label for="rememberMe">${msg("rememberMe")}</label>
                    </div>
                </#if>

                <input type="hidden" id="id-hidden-input" name="credentialId"
                       <#if auth?has_content && auth.selectedCredential?has_content>value="${auth.selectedCredential}"</#if> />

                <button tabindex="4" class="oc-btn-primary" name="login" id="kc-login" type="submit">
                    ${msg("doLogIn")}
                </button>
            </form>
        </#if>

    <#elseif section = "info">
        <#if realm.password && realm.registrationAllowed && !registrationDisabled??>
            <div class="oc-footer-note">
                <span>${msg("noAccount")}</span>
                <a tabindex="6" href="${url.registrationUrl}" class="oc-link">${msg("doRegister")}</a>
            </div>
        </#if>
    </#if>

</@layout.registrationLayout>
