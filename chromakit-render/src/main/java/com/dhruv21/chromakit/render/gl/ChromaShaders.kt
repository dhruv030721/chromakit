package com.dhruv21.chromakit.render.gl

object ChromaShaders {

    const val VERTEX_SHADER = """
        attribute vec4 aPosition;
        attribute vec2 aTexCoord;
        varying vec2 vTexCoord;
        uniform mat4 uMatrix;

        void main() {
            gl_Position = uMatrix * aPosition;
            vTexCoord = aTexCoord;
        }
    """

    const val FRAGMENT_SHADER = """
        precision mediump float;
        varying vec2 vTexCoord;

        uniform sampler2D uCameraTexture;
        uniform sampler2D uMaskTexture;
        uniform sampler2D uBgTexture;

        uniform int uBgType; // 0=None, 1=Color, 2=Image, 3=Blur, 4=Transparent
        uniform vec4 uBgColor;
        uniform float uThreshold;
        uniform float uFeather;
        uniform float uBlurRadius;
        uniform vec2 uTexelSize;
        uniform int uHasMask;

        vec4 sampleBlurredCamera(vec2 uv, float radius) {
            vec4 sum = vec4(0.0);
            vec2 offset = uTexelSize * radius;
            sum += texture2D(uCameraTexture, uv + vec2(-offset.x, -offset.y)) * 0.12;
            sum += texture2D(uCameraTexture, uv + vec2(0.0, -offset.y)) * 0.13;
            sum += texture2D(uCameraTexture, uv + vec2(offset.x, -offset.y)) * 0.12;
            sum += texture2D(uCameraTexture, uv + vec2(-offset.x, 0.0)) * 0.13;
            sum += texture2D(uCameraTexture, uv) * 0.20;
            sum += texture2D(uCameraTexture, uv + vec2(offset.x, 0.0)) * 0.13;
            sum += texture2D(uCameraTexture, uv + vec2(-offset.x, offset.y)) * 0.12;
            sum += texture2D(uCameraTexture, uv + vec2(0.0, offset.y)) * 0.13;
            sum += texture2D(uCameraTexture, uv + vec2(offset.x, offset.y)) * 0.12;
            return sum;
        }

        void main() {
            vec4 cameraColor = texture2D(uCameraTexture, vTexCoord);

            if (uBgType == 0 || uHasMask == 0) {
                gl_FragColor = cameraColor;
                return;
            }

            // Sample segmentation mask (single channel alpha confidence)
            float rawMask = texture2D(uMaskTexture, vTexCoord).r;

            // Feather mask edges using smoothstep
            float edgeLow = clamp(uThreshold - uFeather, 0.0, 1.0);
            float edgeHigh = clamp(uThreshold + uFeather, 0.0, 1.0);
            float personAlpha = smoothstep(edgeLow, edgeHigh, rawMask);

            // Determine virtual background color
            vec4 bgColor;
            if (uBgType == 1) {
                bgColor = uBgColor;
            } else if (uBgType == 2) {
                bgColor = texture2D(uBgTexture, vTexCoord);
            } else if (uBgType == 3) {
                bgColor = sampleBlurredCamera(vTexCoord, uBlurRadius);
            } else if (uBgType == 4) {
                bgColor = vec4(0.0, 0.0, 0.0, 0.0);
            } else {
                bgColor = cameraColor;
            }

            // Alpha composite person foreground over custom background
            gl_FragColor = mix(bgColor, cameraColor, personAlpha);
        }
    """
}
