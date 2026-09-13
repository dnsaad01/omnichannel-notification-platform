<#macro registrationLayout bodyClass="" displayInfo=false displayMessage=true displayRequiredFields=false showAnotherWayIfPresent=true>
<!DOCTYPE html>
<html class="${properties.kcHtmlClass!}"<#if realm.internationalizationEnabled> lang="${locale.currentLanguageTag}"</#if>>

<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width,initial-scale=1">
    <meta name="robots" content="noindex, nofollow">
    <title>${msg("loginTitle",(realm.displayName!"Omnicanal"))}</title>
    <link rel="icon" href="${url.resourcesPath}/img/favicon.svg" type="image/svg+xml">
    <#if properties.styles?has_content>
        <#list properties.styles?split(' ') as style>
            <link href="${url.resourcesPath}/${style}" rel="stylesheet">
        </#list>
    </#if>
    <#if properties.scripts?has_content>
        <#list properties.scripts?split(' ') as script>
            <script src="${url.resourcesPath}/${script}" type="text/javascript"></script>
        </#list>
    </#if>
</head>

<body class="oc-body">
    <div class="oc-page">

        <div class="oc-brandbar">
            <img src="${url.resourcesPath}/img/logo.svg" alt="Omnicanal" class="oc-brand-logo">
            <span class="oc-brand-name">Omni<span class="oc-brand-accent">canal</span></span>
        </div>

        <main class="oc-main">
            <div class="oc-card" role="main">

                <div class="oc-card-accent"></div>

                <div class="oc-card-body">

                    <h1 class="oc-title">
                        <#nested "header">
                    </h1>

                    <#-- global / form-level messages (errors, warnings, success) -->
                    <#if displayMessage && message?has_content && (message.type != 'warning' || !isAppInitiatedAction??)>
                        <div class="oc-alert oc-alert-${message.type}" role="alert">
                            <#if message.type = 'success'><span class="oc-alert-icon">&#10003;</span></#if>
                            <#if message.type = 'warning'><span class="oc-alert-icon">&#9888;</span></#if>
                            <#if message.type = 'error'><span class="oc-alert-icon">&#33;</span></#if>
                            <span class="oc-alert-text">${kcSanitize(message.summary)?no_esc}</span>
                        </div>
                    </#if>

                    <#nested "form">

                    <#if displayInfo>
                        <div class="oc-info">
                            <#nested "info">
                        </div>
                    </#if>

                    <#-- social / external identity providers, if configured on the realm -->
                    <#if realm.password && social.providers?? && social.providers?has_content>
                        <div class="oc-divider"><span>${msg("identity-provider-login-label")}</span></div>
                        <ul class="oc-social-list">
                            <#list social.providers as p>
                                <li>
                                    <a id="social-${p.alias}" class="oc-social-btn" type="button" href="${p.loginUrl}">
                                        <#if p.iconClasses?has_content><i class="${p.iconClasses}"></i></#if>
                                        <span>${p.displayName}</span>
                                    </a>
                                </li>
                            </#list>
                        </ul>
                    </#if>

                    <#if auth?has_content && auth.showTryAnotherWayLink() && showAnotherWayIfPresent>
                        <form id="kc-select-try-another-way-form" action="${url.loginAction}" method="post" class="oc-another-way">
                            <input type="hidden" name="tryAnotherWay" value="on">
                            <a href="#" class="oc-link" onclick="document.forms['kc-select-try-another-way-form'].submit();return false;">
                                ${msg("doTryAnotherWay")}
                            </a>
                        </form>
                    </#if>

                </div>
            </div>

            <#if realm.internationalizationEnabled && locale.supported?size gt 1>
                <div class="oc-locale">
                    <label for="oc-locale-select" class="oc-sr-only">${msg("languages")}</label>
                    <select id="oc-locale-select" class="oc-locale-select"
                            onchange="if(this.value) window.location.href=this.value;">
                        <#list locale.supported as l>
                            <option value="${l.url}" <#if l.languageTag = locale.currentLanguageTag>selected</#if>>${l.label}</option>
                        </#list>
                    </select>
                </div>
            </#if>
        </main>

        <footer class="oc-footer">
            <span>&copy; ${.now?string("yyyy")} Omnicanal &mdash; Plateforme de notifications omnicanales et de workflows événementiels</span>
        </footer>

    </div>
</body>
</html>
</#macro>
