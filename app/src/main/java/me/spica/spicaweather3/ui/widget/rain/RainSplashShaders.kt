package me.spica.spicaweather3.ui.widget.rain

/** 同一套低饱和水材质用于融合水花和不支持浮点 FBO 时的单颗水珠。 */
internal object RainSplashShaders {
    private val lighting = """
        vec4 shadeWater(vec3 normal, float coverage, float rim, float visibility) {
            vec3 lightDir = normalize(vec3(-0.35, -0.55, 1.0));
            vec3 halfDir = normalize(lightDir + vec3(0.0, 0.0, 1.0));
            float specular = pow(max(dot(normal, halfDir), 0.0), 64.0);
            vec3 bodyColor = vec3(0.72, 0.82, 0.90);
            vec3 highlightColor = vec3(0.96, 0.98, 1.0);
            vec3 color = mix(bodyColor, highlightColor, clamp(rim * 0.25 + specular * 0.75, 0.0, 1.0));
            // 主体透出背景，边缘与少量高光提供水的轮廓，不随密度堆叠成不透明色块。
            float opacity = 0.10 + rim * 0.16 + specular * 0.28;
            return vec4(color, coverage * visibility * opacity);
        }
    """.trimIndent()

    val particleVertex = """
        #version 300 es
        layout(location = 0) in vec2 a_position;
        layout(location = 1) in float a_energy;
        layout(location = 2) in float a_opacity;
        layout(location = 3) in float a_scale;
        uniform vec2 u_resolution;
        uniform float u_pt_base;
        uniform float u_pt_range;
        out float v_energy;
        out float v_opacity;
        void main() {
            vec2 ndc = (a_position / u_resolution) * 2.0 - 1.0;
            gl_Position = vec4(ndc.x, -ndc.y, 0.0, 1.0);
            gl_PointSize = max(1.0, (u_pt_base + a_energy * u_pt_range) * a_scale);
            v_energy = a_energy;
            v_opacity = a_opacity;
        }
    """.trimIndent()

    val splatFragment = """
        #version 300 es
        precision highp float;
        in float v_energy;
        in float v_opacity;
        out vec4 fragColor;
        void main() {
            vec2 p = gl_PointCoord * 2.0 - 1.0;
            float r2 = dot(p, p);
            if (r2 > 1.0) discard;
            float density = exp(-r2 * 2.5) * mix(0.70, 0.95, v_energy);
            // RG 保存加权法线 XY，B 保存加权可见度，A 保存融合密度。
            // 渐隐不参与密度阈值，避免通过抬高阈值来模拟透明度导致突然消失。
            fragColor = vec4(p * density, v_opacity * density, density);
        }
    """.trimIndent()

    val compositeFragment = """
        #version 300 es
        precision highp float;
        uniform sampler2D u_fbo_tex;
        uniform float u_threshold;
        in vec2 v_uv;
        out vec4 fragColor;
        $lighting
        void main() {
            vec4 acc = texture(u_fbo_tex, v_uv);
            float density = acc.a;
            if (density < 0.005) discard;
            float coverage = smoothstep(u_threshold * 0.65, u_threshold * 1.25, density);
            float visibility = clamp(acc.b / density, 0.0, 1.0);
            if (coverage * visibility < 0.001) discard;
            vec2 normalXY = clamp(acc.rg / density, vec2(-1.0), vec2(1.0));
            float normalZ = sqrt(max(1.0 - dot(normalXY, normalXY), 0.001));
            vec3 normal = normalize(vec3(normalXY, normalZ));
            float rim = 1.0 - smoothstep(u_threshold * 0.90, u_threshold * 1.80, density);
            fragColor = shadeWater(normal, coverage, rim, visibility);
        }
    """.trimIndent()

    val fallbackFragment = """
        #version 300 es
        precision highp float;
        in float v_opacity;
        out vec4 fragColor;
        $lighting
        void main() {
            vec2 p = gl_PointCoord * 2.0 - 1.0;
            float r2 = dot(p, p);
            if (r2 > 1.0) discard;
            vec3 normal = vec3(p, sqrt(max(1.0 - r2, 0.0)));
            float coverage = 1.0 - smoothstep(0.65, 1.0, r2);
            float rim = smoothstep(0.18, 0.92, r2);
            fragColor = shadeWater(normal, coverage, rim, v_opacity);
        }
    """.trimIndent()
}
