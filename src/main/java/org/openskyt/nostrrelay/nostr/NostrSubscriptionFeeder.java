package org.openskyt.nostrrelay.nostr;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.openskyt.nostrrelay.dto.ReqFilter;
import org.openskyt.nostrrelay.dto.Subscription;
import org.openskyt.nostrrelay.model.Event;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

/**
 * This class handles feeding clients with subscribed data
 */
@Component
@RequiredArgsConstructor
public class NostrSubscriptionFeeder {

    private final NostrPersistence persistence;
    private final NostrSubscriptionDataManager subscriptionDataManager;
    private final NostrUtil util;

    /**
     * Feeds newly created subscription with REQuested retrieved existing EVENT-data
     *
     */
    @Transactional
    public void sendPersistedData(Subscription subscription) {
        sendEvents(subscription, persistence.getAllEvents(subscription.filters()));
        try {
            subscription.session().sendMessage(util.eoseMessage(subscription));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Feeds current subscriptions with REQuested incoming EVENT-data
     *
     * @param event examined EVENT-data to filter
     */
    public void handleNewEvent(Event event) {
        // filter incoming event by each ReqData in Map<Subscription, Set<ReqData>> - for each sub individually
        for (Subscription subscription : subscriptionDataManager.getAllSubscriptions()) {

            if(doesMatch(event, subscription.filters())) {
                sendEvents(subscription, Set.of(event));
            }
        }
    }

    /**
     * Compares EVENT-data to REQ-data specifics. Sets the right subscription data to event after filtering. Note there might be more REQ-data for single subscription. This method is meant to be cast inside subscription handling methods. (sub method)
     *
     * @param event  EVENT-data to examine
     * @param reqFilterSet REQ-data SET to filter by
     * @return compatible EVENT-data
     */
    private boolean doesMatch(Event event, Set<ReqFilter> reqFilterSet) {
        // if the filter is blank, formal logic dictates that everything shall pass.
        if (reqFilterSet == null) {
            return false;
        }

        for (var r : reqFilterSet) {
            if ((r.getKinds() == null || r.getKinds().isEmpty() || r.getKinds().contains(event.getKind()))                      // kinds filter
                    && (r.getAuthors() == null || r.getAuthors().isEmpty() || r.getAuthors().contains(event.getPubkey()))) {    // authors filter

                return true;
            }
        }

        return false;
    }

    /**
     * Sends EVENT-data SET to client (sub method)
     *
     */
    private void sendEvents(Subscription subscription, Set<Event> events) {
        events.forEach(event -> {
            try {
                subscription.session().sendMessage(util.stringifyEvent(event, subscription));
            } catch (IOException e) {
                throw new RuntimeException();
            }
        });
    }
}
