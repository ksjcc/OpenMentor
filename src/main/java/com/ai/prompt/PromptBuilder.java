package com.ai.prompt;
import com.entity.MemoryChunk;
import org.springframework.stereotype.Component;


import java.util.List;
@Component
public class PromptBuilder {

     public String buildTechInterviewInput(String profileJson,String summaryMd,List<MemoryChunk> memories,String topic) {
        StringBuilder memoryText = new StringBuilder();
        /** 历史记忆 */
        if (memories != null) {
            for (MemoryChunk m : memories) {
                memoryText.append("- ").append(m.getContent()).append("\n");
            }
        }

        return """
                【用户画像】
                %s

                【长期总结】
                %s

                【历史记忆】
                %s

                【当前主题】
                %s

                请基于以上信息生成一个面试问题：
                """.formatted(profileJson, summaryMd, memoryText.toString(), topic);
    }
}
