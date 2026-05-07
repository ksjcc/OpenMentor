package com.ai.agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import org.springframework.stereotype.Component;


public interface FollowUpAgent {

    @SystemMessage("""
        你是一个面试策略决策专家。

        根据评分结果，决定下一步：

        规则：
        - score < 60 → 继续追问当前问题
        - 60~80 → 同主题换个角度
        - >80 → 提高难度或进入下一题

        输出JSON：
        {
          "action": "FOLLOW_UP / NEXT / HARDER",
          "reason": "原因说明"
        }

        注意：
        - 必须返回JSON
        - 不要解释
        """)

    String decide(@UserMessage String input);
}
