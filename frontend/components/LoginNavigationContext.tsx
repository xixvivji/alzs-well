"use client";

import { createContext, useContext, useEffect, useMemo, useState, type Dispatch, type ReactNode, type SetStateAction } from "react";

type ReviewContext = { caseId?: string | null; customerId?: string; alertId?: string };
const Context = createContext<{ review: ReviewContext; setReview: Dispatch<SetStateAction<ReviewContext>> } | null>(null);

export function LoginNavigationProvider({ children }: { children: ReactNode }) {
  const [review, setReview] = useState<ReviewContext>({});
  const value = useMemo(() => ({ review, setReview }), [review]);
  return <Context.Provider value={value}>{children}</Context.Provider>;
}

export function useLoginReviewContext() { return useContext(Context)?.review; }

// Keeps the current review destination in the header menu without adding a visible task panel.
export function ReviewLoginContext({ caseId, customerId, alertId }: ReviewContext) {
  const setReview = useContext(Context)?.setReview;
  useEffect(() => {
    setReview?.({ caseId, customerId, alertId });
    return () => setReview?.({});
  }, [setReview, caseId, customerId, alertId]);
  return null;
}
