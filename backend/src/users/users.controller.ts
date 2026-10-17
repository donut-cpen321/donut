import { type Request, type Response, type NextFunction, Router} from 'express';
import { login } from './users.service';

// Routes: sign-up, profile, courts, availability, sports, notifications preferences.
export const usersRouter = Router();

usersRouter.use((req: Request, res: Response, next: NextFunction) => {
    next();
})

usersRouter.post('/login', (req: Request, res: Response) => {
    login(req, res);
});
