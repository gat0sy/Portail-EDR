/*
 * PROTOTYPE UNIQUEMENT.
 * Ce script simule la session et la navigation entre les maquettes statiques.
 * Il disparaît dans la version Spring Boot : le rôle vient alors de la session
 * (Spring Security), l'affichage conditionnel de Thymeleaf (th:if, sec:authorize)
 * et les formulaires sont traités par les contrôleurs.
 *
 * Rôle simulé : paramètre d'URL ?role=visiteur|notaire|conservateur|administrateur,
 * sinon la valeur data-role déclarée sur la balise <html> de la page.
 */
(function () {
    var root = document.documentElement;
    var asked = new URLSearchParams(location.search).get('role');
    if (asked) root.dataset.role = asked;
    var role = root.dataset.role || 'visiteur';

    function withRole(url) {
        if (/^(https?:|mailto:|tel:|#)/.test(url) || url.indexOf('role=') > -1) return url;
        var parts = url.split('#');
        var sep = parts[0].indexOf('?') > -1 ? '&' : '?';
        return parts[0] + sep + 'role=' + role + (parts[1] ? '#' + parts[1] : '');
    }

    document.addEventListener('DOMContentLoaded', function () {
        /* Le rôle courant suit l'utilisateur de page en page. */
        document.querySelectorAll('a[href]').forEach(function (a) {
            a.setAttribute('href', withRole(a.getAttribute('href')));
        });

        /* Les champs d'un bloc masqué pour ce rôle ne bloquent pas la validation. */
        document.querySelectorAll('[data-for], [data-auth]').forEach(function (el) {
            if (getComputedStyle(el).display !== 'none') return;
            el.querySelectorAll('input, select, textarea, button').forEach(function (c) {
                c.disabled = true;
            });
        });

        /* Bureau compétent déduit du code du livre foncier (fin du numéro du titre).
           Prototype uniquement : dans l'application, le service fait cette
           correspondance à partir de la table LIVRE_FONCIER. Codes provisoires. */
        var tf = document.getElementById('tf'), out = document.getElementById('tf-bureau');
        if (tf && out) {
            var livres = { DK: 'Bureau de Dakar-Plateau', GR: 'Bureau de Grand Dakar', NGA: 'Bureau de Ngor-Almadies',
                DP: 'Bureau de Pikine', GW: 'Bureau de Pikine', R: 'Bureau de Rufisque', TH: 'Bureau de Thiès' };
            var majBureau = function () {
                var m = /^\s*[\d .]+\s*\/\s*([A-Za-z]{1,4})\s*$/.exec(tf.value);
                var b = m && livres[m[1].toUpperCase()];
                out.textContent = b || (m ? 'Livre foncier inconnu' : 'Numéro incomplet (ex. 12.405/NGA)');
                out.classList.toggle('bad', !b);
            };
            tf.addEventListener('input', majBureau);
            majBureau();
        }

        /* Aucun serveur : la soumission d'un formulaire mène à la page suivante. */
        document.querySelectorAll('form').forEach(function (form) {
            form.addEventListener('submit', function (e) {
                e.preventDefault();
                var target = (e.submitter && e.submitter.getAttribute('formaction')) || form.getAttribute('action');
                location.href = withRole(target);
            });
        });
    });
})();
