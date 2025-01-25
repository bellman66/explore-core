
-- Create Table chat_message
create table chat_message
(
    account_id bigint       not null,
    created_at timestamp(6) not null,
    id         bigint       not null,
    topic_id   bigint,
    updated_at timestamp(6),
    content    varchar(255) not null,
    input      varchar(255) not null,
    CONSTRAINT pk_topic_id PRIMARY KEY ("id", topic_id, created_at)
) partition by list(topic_id);


-- Create Partition table chat_message_01
CREATE TABLE chat_message_01 PARTITION OF chat_message
    FOR VALUES IN ('01')
    partition by range(created_at);

CREATE TABLE chat_message_01_202501 PARTITION OF chat_message_01
    FOR VALUES FROM ('2025-01-01') TO ('2025-02-01');

CREATE TABLE chat_message_01_202502 PARTITION OF chat_message_01
    FOR VALUES FROM ('2025-02-01') TO ('2025-03-01');


-- Create Partition table chat_message_02
CREATE TABLE chat_message_02 PARTITION OF chat_message
    FOR VALUES IN ('02')
    partition by range(created_at);

CREATE TABLE chat_message_02_202501 PARTITION OF chat_message_02
    FOR VALUES FROM ('2025-01-01') TO ('2025-02-01');

CREATE TABLE chat_message_02_202502 PARTITION OF chat_message_02
    FOR VALUES FROM ('2025-02-01') TO ('2025-03-01');


-- test insert data
insert into chat_message values (1, now(), 1, 1, now(), 'hello', 'hello');
insert into chat_message values (1, now(), 2, 2, now(), 'hello', 'hello');