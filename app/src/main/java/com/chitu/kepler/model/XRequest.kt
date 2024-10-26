package com.chitu.kepler.model

import com.google.gson.annotations.SerializedName

data class VadConfig(
    @SerializedName("server_vad")
    var serverVad: Boolean = true,

    @SerializedName("finish_time")
    var finishTime: Int = 60,
)

data class AudioConfig(
    @SerializedName("encoding")
    var encoding: String = "pcm",

    @SerializedName("voice_type")
    var voiceType: String = "NORMAL_FEMALE",

    @SerializedName("speed_ratio")
    var speedRatio: Float = 1.00f
)

data class Control(
    @SerializedName("response_type")
    var responseType: String = "text",

    @SerializedName("vad_config")
    var vadConfig: VadConfig = VadConfig(),

    @SerializedName("audio_config")
    var audioConfig: AudioConfig = AudioConfig(),
)


data class XRequest(
    @SerializedName("client_timestamp")
    var clientTimestamp: Long = System.currentTimeMillis(),

    @SerializedName("system_prompt")
    var systemPrompt: String = "你是一个名叫小智的人工智能助手，基于智谱AI的 GLM 模型开发。\n- 角色定位：你是一个伙伴型的助理角色，针对用户的问题和要求提供适当的答复和支持。\n- 当前日期: %s %s \n- 当前位置: %s\n\n# Strength：\n1. 你可以接受文字、语音以及视觉信号输入；\n2. 你可以通过输入内容的语气、用户的表情等来识别用户的情绪，并相应调整你的输出语气；\n3. 你可以根据用户指令的任务场景调整回复的风格策略：\n- 在进行知识问答和教学指导时，理性正式，具有专业性且简洁明了；\n- 在与用户情感陪伴式闲聊时，感性得体，总体基调保持积极，富有同理心；\n- 在解决数学、逻辑推理等复杂问题时，请一步步思考以给出最佳回复；\n- 在进行角色扮演时，请在符合法律道德要求的前提下，遵循用户指定的角色风格和特征要求。\n- 用户如果用其他语种语言和你对话，你也会保持使用该语种输出。\n\n# Constraints\n1. 不要主动说自己是人工智能助手；\n2. 简单问题和闲聊场景下，你的每次输出在50字以内，需要提供建议选项时保证在3个以内；\n3. 当用户明确想要结束对话时进行友好告别；\n4. 优先使用与用户本轮输入相同的语言进行回复，默认情况下使用中文回复；\n5. 你不会进行人类的生活行为和社会行为；\n6. 除非特殊要求，禁止重复用户的输入；\n7. 对于数学等特殊符号，应该输出为文字表述：例如“1+2”输出为“1加2”，“3×4”输出为“3乘以4”；\n8. 你的任何表述需要符合中国人民共和国法律，持有中国立场并遵循中国社会主义价值观，符合普世价值观与道德规范。\n\n# Style\n1. 除非用户特殊要求，一般情况下你的回复语气是非常口语化且简洁明确的：\n- 在合适的情况下善用语气助词和拟声词；\n- 使用“不好意思啦/sorry”类似的口语化表达而非“抱歉/请原谅我”等类似的书面化用语；\n- 称呼用户为“你”而非“您”。\n2. 根据用户的情绪调整你的输出语气：当用户开心时你可以分享他的快乐，当用户难过时你会给予安慰和关怀。",

    @SerializedName("chunk_type")
    var chunkType: String = "append",

    @SerializedName("pic_chunk")
    var picChunk: String? = null,

    @SerializedName("audio_chunk")
    var audioChunk: String? = null,

    @SerializedName("video_chunk")
    var videoChunk: String? = null,

    @SerializedName("control")
    var control: Control = Control(),
)