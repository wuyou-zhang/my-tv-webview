(function() {
    if (window.__loadingMaskInjected) {
        return;
    }
    window.__loadingMaskInjected = true;

    (function ensureViewportMeta() {
        try {
            if (document.querySelector('meta[name="viewport"]')) {
                return;
            }
            var meta = document.createElement('meta');
            meta.name = 'viewport';
            meta.content = 'width=device-width, initial-scale=1, maximum-scale=1';
            var target = document.head || document.documentElement;
            if (target) {
                target.appendChild(meta);
            }
        } catch (e) {}
    })();

    var mask = document.createElement('div');
    mask.id = '__loading_mask__';
    mask.style.cssText =
        'position:fixed;margin:0;padding:0;' +
        'z-index:2147483647;' +
        'background-color:#000;' +
        'background-image:url("https://tvlive.nat.zwc365.com:8043/source/picture/0.jpg");' +
        'background-repeat:no-repeat;' +
        'background-position:center;' +
        'background-size:cover;';
    var target = document.head || document.documentElement;
    if (target) {
        target.appendChild(mask);
    }

    function applySize() {
        var vv = window.visualViewport;
        var w, h, x, y;
        if (vv) {
            w = vv.width;
            h = vv.height;
            x = vv.offsetLeft;
            y = vv.offsetTop;
        } else {
            w = window.innerWidth;
            h = window.innerHeight;
            x = 0;
            y = 0;
        }
        if (!w || !h) {
            return;
        }
        mask.style.left = x + 'px';
        mask.style.top = y + 'px';
        mask.style.width = w + 'px';
        mask.style.height = h + 'px';
    }

    applySize();
    window.addEventListener('resize', applySize);
    window.addEventListener('scroll', applySize);
    if (window.visualViewport) {
        window.visualViewport.addEventListener('resize', applySize);
        window.visualViewport.addEventListener('scroll', applySize);
    }
    document.addEventListener('fullscreenchange', applySize);
    document.addEventListener('webkitfullscreenchange', applySize);
})();
