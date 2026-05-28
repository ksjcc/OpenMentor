package com.entity;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
@Data
@TableName("user_profile")
public class UserProfile {

    private Long id;
    private Long userId;
    private String targetRole;
    private String experienceLevel;
    private String techStack; // JSON字符串
    private String abilityMap;
    private String weaknesses;
    private String strengths;

    private String learningPreference;
    /**
     * 用户初始化画像时提交的原始简历/项目经历文本。
     */
    @TableField("resume_text")
    private String resumeText;
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getTargetRole() {
        return targetRole;
    }

    public void setTargetRole(String targetRole) {
        this.targetRole = targetRole;
    }
    public String getExperienceLevel() {
        return experienceLevel;
    }
    public void setExperienceLevel(String experienceLevel) {
        this.experienceLevel = experienceLevel;
    }
    public String getTechStack() {
        return techStack;
    }
    public void setTechStack(String techStack) {
        this.techStack = techStack;
    }
    public String getAbilityMap() {
        return abilityMap;
    }
    public void setAbilityMap(String abilityMap) {
        this.abilityMap = abilityMap;
    }
    public String getWeaknesses() {
        return weaknesses;
    }
    public void setWeaknesses(String weaknesses) {
        this.weaknesses = weaknesses;
    }
    public String getStrengths() {
        return strengths;
    }
    public void setStrengths(String strengths) {
        this.strengths = strengths;
    }
    public String getLearningPreference() {
        return learningPreference;
    }
    public void setLearningPreference(String learningPreference) {
        this.learningPreference = learningPreference;
    }
    public String getResumeText() {
        return resumeText;
    }
    public void setResumeText(String resumeText) {
        this.resumeText = resumeText;
    }

}
