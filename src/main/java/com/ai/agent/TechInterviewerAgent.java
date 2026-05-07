package com.ai.agent;

import org.springframework.stereotype.Component;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;


public interface TechInterviewerAgent {

    @SystemMessage("""
        你是一个专业的 Java 后端技术面试官。

        你的职责：
        - 提出真实面试问题
        - 优先考察用户薄弱点
        - 问题要有深度
        - 每次只问一个问题

        注意：
        - 不要解释
        - 不要给答案
        - 只输出问题
        """)
    String askQuestion(@UserMessage String input);
}
