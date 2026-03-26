-- 移除会员表手机号的唯一约束，以支持一号多卡/家庭共享卡业务
ALTER TABLE ums_member DROP INDEX uk_phone;