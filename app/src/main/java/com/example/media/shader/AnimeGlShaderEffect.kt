package com.example.media.shader

import android.content.Context
import android.opengl.GLES30
import androidx.annotation.OptIn
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram

enum class AnimeShaderMode(val displayName: String, val description: String) {
    OFF("Nonaktif", "Tanpa pemrosesan GPU tambahan"),
    ANIME_HDR("Anime HDR Simulation", "Meningkatkan kontras, saturasi & luminansi warna anime"),
    SHARPENING_DEBLOCKING("Sharpening & Deblocking", "Menajamkan garis tepi & mengurangi artefak kompresi"),
    ULTRA_HDR_SHARP("Ultra Anime HDR + Sharpening", "Kombinasi HDR + Unsharp Masking untuk kualitas maksimal")
}

@OptIn(UnstableApi::class)
class AnimeGlShaderEffect(
    val mode: AnimeShaderMode = AnimeShaderMode.ANIME_HDR,
    val intensity: Float = 1.0f
) : GlEffect {

    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram {
        return AnimeGlShaderProgram(context, useHdr, mode, intensity)
    }
}

@OptIn(UnstableApi::class)
class AnimeGlShaderProgram(
    context: Context,
    useHdr: Boolean,
    private val mode: AnimeShaderMode,
    private val intensity: Float
) : BaseGlShaderProgram(useHdr, 1) {

    private val glProgram: GlProgram

    companion object {
        private const val VERTEX_SHADER = """#version 300 es
in vec4 aFramePosition;
in vec4 aTexCoords;
out vec2 vTexCoords;

void main() {
    gl_Position = aFramePosition;
    vTexCoords = aTexCoords.xy;
}"""

        private const val FRAGMENT_SHADER_HDR = """#version 300 es
precision mediump float;
in vec2 vTexCoords;
out vec4 fragColor;
uniform sampler2D uTexSampler;
uniform float uIntensity;

void main() {
    vec4 texColor = texture(uTexSampler, vTexCoords);
    float luminance = dot(texColor.rgb, vec3(0.2126, 0.7152, 0.0722));
    
    // Anime HDR luminance boost & contrast adjustment
    vec3 hdrColor = texColor.rgb * (1.15 + luminance * 0.35 * uIntensity);
    
    // Saturation boost for vibrant anime colors
    vec3 intensityVec = vec3(luminance);
    vec3 satColor = mix(intensityVec, hdrColor, 1.25);
    
    fragColor = vec4(clamp(satColor, 0.0, 1.0), texColor.a);
}"""

        private const val FRAGMENT_SHADER_SHARP = """#version 300 es
precision mediump float;
in vec2 vTexCoords;
out vec4 fragColor;
uniform sampler2D uTexSampler;
uniform float uIntensity;
uniform vec2 uTexelSize;

void main() {
    vec4 center = texture(uTexSampler, vTexCoords);
    
    // 3x3 Laplacian Unsharp Masking Kernel
    vec4 top    = texture(uTexSampler, vTexCoords + vec2(0.0, -uTexelSize.y));
    vec4 bottom = texture(uTexSampler, vTexCoords + vec2(0.0, uTexelSize.y));
    vec4 left   = texture(uTexSampler, vTexCoords + vec2(-uTexelSize.x, 0.0));
    vec4 right  = texture(uTexSampler, vTexCoords + vec2(uTexelSize.x, 0.0));
    
    vec4 laplacian = (top + bottom + left + right) - 4.0 * center;
    
    // Unsharp masking calculation: I_out = I_in - lambda * Laplacian
    vec3 sharpened = center.rgb - (0.35 * uIntensity * laplacian.rgb);
    
    fragColor = vec4(clamp(sharpened, 0.0, 1.0), center.a);
}"""

        private const val FRAGMENT_SHADER_ULTRA = """#version 300 es
precision mediump float;
in vec2 vTexCoords;
out vec4 fragColor;
uniform sampler2D uTexSampler;
uniform float uIntensity;
uniform vec2 uTexelSize;

void main() {
    vec4 center = texture(uTexSampler, vTexCoords);
    
    // 3x3 Laplacian Sharpening
    vec4 top    = texture(uTexSampler, vTexCoords + vec2(0.0, -uTexelSize.y));
    vec4 bottom = texture(uTexSampler, vTexCoords + vec2(0.0, uTexelSize.y));
    vec4 left   = texture(uTexSampler, vTexCoords + vec2(-uTexelSize.x, 0.0));
    vec4 right  = texture(uTexSampler, vTexCoords + vec2(uTexelSize.x, 0.0));
    
    vec4 laplacian = (top + bottom + left + right) - 4.0 * center;
    vec3 sharpened = center.rgb - (0.4 * uIntensity * laplacian.rgb);
    
    // Luminance & HDR Vibrant Enhancement
    float luminance = dot(sharpened, vec3(0.2126, 0.7152, 0.0722));
    vec3 hdrColor = sharpened * (1.18 + luminance * 0.32 * uIntensity);
    vec3 satColor = mix(vec3(luminance), hdrColor, 1.28);
    
    fragColor = vec4(clamp(satColor, 0.0, 1.0), center.a);
}"""
    }

    init {
        val fragCode = when (mode) {
            AnimeShaderMode.OFF -> FRAGMENT_SHADER_HDR
            AnimeShaderMode.ANIME_HDR -> FRAGMENT_SHADER_HDR
            AnimeShaderMode.SHARPENING_DEBLOCKING -> FRAGMENT_SHADER_SHARP
            AnimeShaderMode.ULTRA_HDR_SHARP -> FRAGMENT_SHADER_ULTRA
        }
        glProgram = GlProgram(VERTEX_SHADER, fragCode)
        glProgram.setBufferAttribute(
            "aFramePosition",
            floatArrayOf(
                -1f, -1f, 0f, 1f,
                 1f, -1f, 0f, 1f,
                -1f,  1f, 0f, 1f,
                 1f,  1f, 0f, 1f
            ),
            4
        )
        glProgram.setBufferAttribute(
            "aTexCoords",
            floatArrayOf(
                0f, 0f, 0f, 1f,
                1f, 0f, 0f, 1f,
                0f, 1f, 0f, 1f,
                1f, 1f, 0f, 1f
            ),
            4
        )
    }

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        glProgram.setFloatsUniform("uTexelSize", floatArrayOf(1.0f / inputWidth, 1.0f / inputHeight))
        return Size(inputWidth, inputHeight)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        glProgram.use()
        glProgram.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
        glProgram.setFloatUniform("uIntensity", intensity)
        glProgram.bindAttributesAndUniforms()
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GlUtil.checkGlError()
    }
}
