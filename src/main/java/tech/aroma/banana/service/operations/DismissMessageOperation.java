/*
 * Copyright 2016 Aroma Tech.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package tech.aroma.banana.service.operations;

import java.util.Set;
import javax.inject.Inject;
import org.apache.thrift.TException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sir.wellington.alchemy.collections.lists.Lists;
import sir.wellington.alchemy.collections.sets.Sets;
import tech.aroma.banana.data.InboxRepository;
import tech.aroma.banana.thrift.exceptions.InvalidArgumentException;
import tech.aroma.banana.thrift.service.DismissMessageRequest;
import tech.aroma.banana.thrift.service.DismissMessageResponse;
import tech.sirwellington.alchemy.arguments.AlchemyAssertion;
import tech.sirwellington.alchemy.thrift.operations.ThriftOperation;

import static tech.aroma.banana.data.assertions.RequestAssertions.isNullOrEmpty;
import static tech.aroma.banana.data.assertions.RequestAssertions.validMessageId;
import static tech.sirwellington.alchemy.arguments.Arguments.checkThat;
import static tech.sirwellington.alchemy.arguments.assertions.Assertions.notNull;
import static tech.sirwellington.alchemy.arguments.assertions.StringAssertions.nonEmptyString;
import static tech.sirwellington.alchemy.arguments.assertions.StringAssertions.validUUID;

/**
 *
 * @author SirWellington
 */
final class DismissMessageOperation implements ThriftOperation<DismissMessageRequest, DismissMessageResponse>
{

    private final static Logger LOG = LoggerFactory.getLogger(DismissMessageOperation.class);
    private final InboxRepository inboxRepo;

    @Inject
    DismissMessageOperation(InboxRepository inboxRepo)
    {
        checkThat(inboxRepo).is(notNull());
        
        this.inboxRepo = inboxRepo;
    }

    @Override
    public DismissMessageResponse process(DismissMessageRequest request) throws TException
    {
        checkThat(request)
            .throwing(ex -> new InvalidArgumentException(ex.getMessage()))
            .is(good());

        String userId = request.token.userId;

        if (request.dismissAll)
        {
            clearInboxFor(userId);
        }
        else
        {
            Set<String> messageIds = getAllMessageIdsFrom(request);

            deleteMessages(userId, messageIds);
            return new DismissMessageResponse().setMessagesDismissed(messageIds.size());
        }

        return new DismissMessageResponse();
    }

    private AlchemyAssertion<DismissMessageRequest> good()
    {
        return request ->
        {
            checkThat(request)
                .is(notNull());
            
            checkThat(request.token)
                .is(notNull());
            
            checkThat(request.token.userId)
                .usingMessage("token is missing userId")
                .is(nonEmptyString())
                .usingMessage("token userId must be a UUID")
                .is(validUUID());
            
            checkThat(request.messageId)
                .is(validMessageId());
            
            if(request.isSetMessageIds())
            {
                for(String id : request.messageIds)
                {
                    checkThat(id).is(validMessageId());
                }
            }
        };
    }

    private void clearInboxFor(String userId) throws TException
    {
        inboxRepo.deleteAllMessagesForUser(userId);
    }

    private Set<String> getAllMessageIdsFrom(DismissMessageRequest request)
    {
        Set<String> result = Sets.create();
        if (!isNullOrEmpty(request.messageId))
        {
            result.add(request.messageId);
        }

        if (!Lists.isEmpty(request.messageIds))
        {
            result.addAll(request.messageIds);
        }

        return result;
    }

    private void deleteMessages(String userId, Set<String> messageIds)
    {
        messageIds.parallelStream()
            .forEach(msgId -> this.deleteMessage(userId, msgId));
    }

    private void deleteMessage(String userId, String msgId)
    {
        try
        {
            inboxRepo.deleteMessageForUser(userId, msgId);
        }
        catch (TException ex)
        {
            LOG.warn("Failed to delete message {} for user {}", msgId, userId, ex);
        }
    }
}
