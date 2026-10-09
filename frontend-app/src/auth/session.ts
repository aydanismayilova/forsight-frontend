/*
 * Client-side session model for Foresight.
 *
 * There is no auth backend yet: sign-in and registration are simulated in the
 * browser. Passwords are validated for shape and then discarded. Only the
 * profile object is ever persisted, never a password or token.
 */

export type AppRole = "Project Lead" | "Admin" | "Developer" | "Member";

export const ROLES: AppRole[] = ["Project Lead", "Admin", "Developer", "Member"];

export const TIMEZONES = [
  "Asia/Baku (GMT+4)",
  "Europe/Istanbul (GMT+3)",
  "Europe/London (GMT+0)",
  "America/New_York (GMT−5)",
  "Asia/Dubai (GMT+4)",
];

export interface NotificationPrefs {
  riskAlerts: boolean;
  weeklyDigest: boolean;
  mentions: boolean;
}

export interface UserProfile {
  id: string;
  fullName: string;
  email: string;
  jobTitle: string;
  role: AppRole;
  timezone: string;
  workspace: string;
  joinedAt: string; // ISO date
  notifications: NotificationPrefs;
}

export type DemoAccount = "lead" | "admin" | "developer";

export const DEMO_ACCOUNTS: Record<DemoAccount, UserProfile> = {
  lead: {
    id: "u-anar",
    fullName: "Anar Mammadov",
    email: "anar@foresight.dev",
    jobTitle: "Backend Lead",
    role: "Project Lead",
    timezone: TIMEZONES[0],
    workspace: "Student Team Q4",
    joinedAt: "2026-02-14",
    notifications: { riskAlerts: true, weeklyDigest: true, mentions: true },
  },
  admin: {
    id: "u-nigar",
    fullName: "Nigar Huseynova",
    email: "nigar@foresight.dev",
    jobTitle: "Workspace Administrator",
    role: "Admin",
    timezone: TIMEZONES[0],
    workspace: "Student Team Q4",
    joinedAt: "2025-11-03",
    notifications: { riskAlerts: true, weeklyDigest: false, mentions: true },
  },
  developer: {
    id: "u-leyla",
    fullName: "Leyla Aliyeva",
    email: "leyla@foresight.dev",
    jobTitle: "Frontend Developer",
    role: "Developer",
    timezone: TIMEZONES[0],
    workspace: "Student Team Q4",
    joinedAt: "2026-03-02",
    notifications: { riskAlerts: true, weeklyDigest: true, mentions: false },
  },
};

export const DEMO_ACCOUNT_LABEL: Record<DemoAccount, string> = {
  lead: "Project Lead",
  admin: "Admin",
  developer: "Developer",
};

export function initialsOf(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) return "?";
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
  return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
}

export const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
export const MIN_PASSWORD = 8;

/** Signs in a demo account by email, or builds a profile for any other address. */
export function profileForEmail(email: string): UserProfile {
  const normalized = email.trim().toLowerCase();
  const demo = Object.values(DEMO_ACCOUNTS).find((u) => u.email === normalized);
  if (demo) return { ...demo };
  const local = normalized.split("@")[0].replace(/[._-]+/g, " ");
  const fullName = local.replace(/\b\w/g, (c) => c.toUpperCase()) || "New User";
  return {
    id: `u-${Date.now().toString(36)}`,
    fullName,
    email: normalized,
    jobTitle: "Team Member",
    role: "Member",
    timezone: TIMEZONES[0],
    workspace: "Student Team Q4",
    joinedAt: new Date().toISOString().slice(0, 10),
    notifications: { riskAlerts: true, weeklyDigest: true, mentions: true },
  };
}

export function newAccount(input: { fullName: string; email: string; workspace: string }): UserProfile {
  return {
    id: `u-${Date.now().toString(36)}`,
    fullName: input.fullName.trim(),
    email: input.email.trim().toLowerCase(),
    jobTitle: "Project Lead",
    role: "Project Lead",
    timezone: TIMEZONES[0],
    workspace: input.workspace.trim(),
    joinedAt: new Date().toISOString().slice(0, 10),
    notifications: { riskAlerts: true, weeklyDigest: true, mentions: true },
  };
}

/* ───── persistence: "Remember me" → localStorage, otherwise this tab only ───── */

const SESSION_KEY = "foresight.session";

function storages(): Storage[] {
  const out: Storage[] = [];
  try {
    out.push(window.localStorage);
  } catch {
    /* blocked */
  }
  try {
    out.push(window.sessionStorage);
  } catch {
    /* blocked */
  }
  return out;
}

export function loadSession(): UserProfile | null {
  for (const store of storages()) {
    try {
      const raw = store.getItem(SESSION_KEY);
      if (raw) return JSON.parse(raw) as UserProfile;
    } catch {
      /* unreadable or corrupt: ignore */
    }
  }
  return null;
}

export function saveSession(user: UserProfile, remember: boolean) {
  clearSession();
  try {
    (remember ? window.localStorage : window.sessionStorage).setItem(SESSION_KEY, JSON.stringify(user));
  } catch {
    /* storage unavailable: session lasts until reload */
  }
}

/** Keeps the session in whichever store it already lives in. */
export function updateSession(user: UserProfile) {
  for (const store of storages()) {
    try {
      if (store.getItem(SESSION_KEY)) store.setItem(SESSION_KEY, JSON.stringify(user));
    } catch {
      /* ignore */
    }
  }
}

export function clearSession() {
  for (const store of storages()) {
    try {
      store.removeItem(SESSION_KEY);
    } catch {
      /* ignore */
    }
  }
}

/* ───── activity log ───── */

export type ActivityKind = "ai" | "plan" | "account";

export interface ActivityEntry {
  id: string;
  at: number; // epoch ms
  kind: ActivityKind;
  text: string;
  detail?: string;
}

const H = 3_600_000;

export function seedActivity(now = Date.now()): ActivityEntry[] {
  return [
    { id: "a1", at: now - 2 * H, kind: "ai", text: "Acknowledged AI Mock API recommendation", detail: "Backend REST API Endpoints · Student Team Project Q4" },
    { id: "a2", at: now - 5 * H, kind: "plan", text: "Ran a What-If scenario: deadline −2 days", detail: "On-time odds dropped to 9%" },
    { id: "a3", at: now - 26 * H, kind: "ai", text: "Dismissed capacity warning for Elvin", detail: "QA load back under 85%" },
    { id: "a4", at: now - 3 * 24 * H, kind: "plan", text: "Confirmed estimate: Backend REST API Endpoints", detail: "30h planned; AI forecasts 48h" },
    { id: "a5", at: now - 6 * 24 * H, kind: "account", text: "Joined workspace Student Team Q4" },
  ];
}

export function timeAgo(ts: number, now = Date.now()): string {
  const m = Math.round((now - ts) / 60_000);
  if (m < 1) return "Just now";
  if (m < 60) return `${m} min${m === 1 ? "" : "s"} ago`;
  const h = Math.round(m / 60);
  if (h < 24) return `${h} hour${h === 1 ? "" : "s"} ago`;
  const d = Math.round(h / 24);
  return d === 1 ? "Yesterday" : `${d} days ago`;
}
