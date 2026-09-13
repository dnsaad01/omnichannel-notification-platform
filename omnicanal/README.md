# Thème de connexion Keycloak — Plateforme Omnicanal

Thème de login Keycloak adapté à l'identité réelle de votre plateforme de notifications omnicanales et de workflows événementiels (Spring Boot / Angular / Kafka / Keycloak) : fond sombre (gray-900 / near-black) et accents orange/amber, alignés sur le thème visuel déjà utilisé dans la console d'administration Angular — pour que l'écran de connexion ne fasse pas rupture avec le reste de l'application.

## Structure des fichiers

```
themes/
└── omnicanal/
    └── login/
        ├── theme.properties
        ├── template.ftl               # structure générale (fond, carte, logo, footer)
        ├── login.ftl                  # formulaire identifiant / mot de passe
        └── resources/
            ├── css/
            │   └── styles.css          # palette dark + orange/amber, tous les styles
            └── img/
                ├── logo.svg            # glyphe "signal omnicanal" — à remplacer si besoin
                └── favicon.svg
```

C'est exactement la même structure et la même logique FreeMarker que la version précédente — seuls le nom du dossier, la palette CSS, le texte de marque et le préfixe des classes CSS (`op-` → `oc-`, pour "**O**mni**c**anal") ont changé. `template.ftl` et `login.ftl` réutilisent toujours les macros/variables standard de Keycloak (`registrationLayout`, `msg(...)`, `messagesPerField`, `social.providers`, etc.), donc la compatibilité avec le moteur de thèmes est intacte.

## À propos du nom "Omnicanal"

Vous n'avez pas donné de nom de marque précis pour la plateforme cette fois-ci (contrairement à "OutilPro.ma" pour la version précédente) — j'ai donc utilisé **"Omnicanal"** comme texte de repère dans la barre de marque et le pied de page, en cohérence avec le nom du projet ("plateforme de notifications omnicanales"). C'est un simple texte, pas un vrai branding : si votre application a un vrai nom de produit, changez-le en une seule fois dans `template.ftl` :

```html
<span class="oc-brand-name">Omni<span class="oc-brand-accent">canal</span></span>
```

et dans le pied de page, un peu plus bas dans le même fichier.

## Palette (centralisée dans `:root`, en haut de `styles.css`)

```css
--oc-bg-900: #0b1220;      /* fond principal (gray-900 / near-black) */
--oc-surface-800: #161f30; /* carte */
--oc-orange-500: #f97316;  /* action principale */
--oc-amber-400: #fbbf24;   /* accent secondaire / focus */
```

Ces valeurs reprennent les teintes Tailwind `gray-900`/`gray-950` et `orange-500`/`amber-400`, pour rester alignées avec la palette déjà en place côté Angular. Si votre design system frontend utilise des teintes légèrement différentes (un `amber-500` plus foncé, un fond `slate` plutôt que `gray`, etc.), donnez-moi les codes hexadécimaux exacts utilisés dans votre `tailwind.config` et j'ajuste ces variables en conséquence — c'est un changement d'une minute puisque tout le fichier référence ces variables plutôt que des couleurs codées en dur.

## Intégration (Docker — votre cas)

```yaml
services:
  keycloak:
    image: quay.io/keycloak/keycloak:24.0.2
    volumes:
      - ./themes/omnicanal:/opt/keycloak/themes/omnicanal
    command: start-dev
```

```bash
docker compose up -d --force-recreate keycloak
```

Puis, dans la console d'administration Keycloak : **Realm settings → Themes → Login theme → `omnicanal` → Save.**

## Itération rapide en développement

Le cache des thèmes est activé par défaut ; une modification de `styles.css` ne sera pas visible sans redémarrage. En développement, désactivez le cache :

```
--spi-theme-cache-themes=false
--spi-theme-cache-templates=false
--spi-theme-static-max-age=-1
```

En production, laissez le cache activé et faites `docker compose restart keycloak` après toute modification.

## Portée couverte

L'écran identifiant/mot de passe classique (mot de passe oublié, se souvenir de moi, inscription, connexion via IdP externe si configuré), les messages d'erreur/succès, et le sélecteur de langue. Les écrans OTP, WebAuthn, révision d'IdP et changement de mot de passe forcé retombent proprement sur le thème `keycloak` par défaut (grâce à `parent=keycloak` dans `theme.properties`) — rien ne casse, mais ils n'ont pas encore la même identité visuelle. Dites-le-moi si vous voulez que je les habille aussi avec le même système de classes `oc-*`.
