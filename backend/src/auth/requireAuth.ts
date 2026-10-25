import { type Request, type Response, type NextFunction } from 'express';
import { verifyAccessToken } from './tokens';

// Add userId to the request object on every route behind requireAuth
declare global {
  namespace Express {
    interface Request {
      userId?: string;
    }
  }
}

const BEARER_PREFIX = 'Bearer ';

/**
 * Rejects requests without a valid access token before they reach the controllers
 * and puts the caller's user id on req.userId
 * 
 * @param req expects `Authorization: Bearer <access token>`
 * @param res 401 if the token is missing, invalid or expired
 * @param next runs the route handler once the user is known
 */
export function requireAuth(req: Request, res: Response, next: NextFunction): void {
  const header = req.headers.authorization;

  // Extract the token from the Authorization header
  const token = header?.startsWith(BEARER_PREFIX) ? header.slice(BEARER_PREFIX.length) : '';
  if (!token) {
    res.status(401).json({ error: 'Missing auth token' });
    return;
  }

  // Verify if the token is valid
  const userId = verifyAccessToken(token);
  if (!userId) {
    res.status(401).json({ error: 'Invalid or expired auth token' });
    return;
  }

  // Add the user id to the request object
  req.userId = userId;
  
  next();
}
